package edu.buffalo.cse.cse486586.simpledht;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;
import java.util.LinkedList;
import java.util.List;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteQueryBuilder;
import android.net.Uri;
import android.os.AsyncTask;
import android.telephony.TelephonyManager;
import android.util.Log;

public class SimpleDhtProvider extends ContentProvider {

    static final String TAG = SimpleDhtProvider.class.getSimpleName();
    static final String[] PORTS = {"11108", "11112", "11116", "11120", "11124"};

    static final int SERVER_PORT = 10000;
    public Uri mUri;
    DBHelper dbHelper;
    String predecessor;
    String successor;
    String myNodeID;
    String myPortID;
    String myHash;
    String startPortID = "11108";
    List<String> syncList=Collections.synchronizedList(new LinkedList());
    boolean receivedAll=false;

    /*
    *https://developer.android.com/reference/android/database/MatrixCursor.html
    * https://docs.oracle.com/javase/tutorial/essential/concurrency/locksync.html
    * https://docs.oracle.com/javase/tutorial/essential/concurrency/guardmeth.html
    * From PA2B
    * https://docs.oracle.com/javase/7/docs/api/java/net/SocketTimeoutException.html
    * https://docs.oracle.com/javase/tutorial/networking/sockets/index.html
    * https://developer.android.com/guide/topics/providers/content-provider-basics.html
    * https://developer.android.com/guide/topics/providers/content-provider-creating.html
    * https://developer.android.com/reference/android/os/AsyncTask.html
    * Used code from my PA2B
     */


    //changes made--- insert conditions replaced with checkIfKeyBelongsToMe()

    SQLiteDatabase sqlDB;
    String [] colNames={"key","value"};

    MatrixCursor matrixCursor2=new MatrixCursor(colNames);
    ContentValues contentValues;


    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        sqlDB=dbHelper.getWritableDatabase();
        int deletedRows=0;
        //check if only one avd - then all delete will be from here
        if(predecessor.equals(myPortID) && successor.equals(myPortID)){
            if(selection.equals("*") || selection.equals("@")){
                //delete all from the avd
                deletedRows = sqlDB.delete(DBHelper.TABLE_NAME, null, null);
            }
            else {
                deletedRows = sqlDB.delete(DBHelper.TABLE_NAME,"key=?",new String[]{selection});
            }
        }else if(selection.equals("@")){
            deletedRows = sqlDB.delete(DBHelper.TABLE_NAME, null, null);
        }else if(selection.equals("*")){

            Log.d(TAG,"DELETE: DELETE ALL");
            deletedRows = sqlDB.delete(DBHelper.TABLE_NAME,"key=?",new String[]{selection});
            Message deleteForward = new Message(myPortID, selection + ":" + myPortID, null, myPortID, "DELETE");
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, deleteForward.toString(), successor);

        }else if(selection.contains("*")){
            Log.d(TAG,"DELETE: DELETE FORWARD");

            String key = selection.split(":")[0];
            String[] selectArgs = {key};
            String originPort = selection.split(":")[1];

            if(originPort.equals(myPortID)){
                Log.d(TAG,"DELETE: DELETE ALL COMPLETE");
            }
            else{
                deletedRows = sqlDB.delete(DBHelper.TABLE_NAME,"key=?",new String[]{selection.split(":")[0]});
                Message deleteForward = new Message(myPortID, selection, null, myPortID, "DELETE");
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, deleteForward.toString(), successor);
            }
        }else if (!selection.contains(":")) {
            //has a key
            Log.d(TAG, "DELETE: DELETE SELECTION IS: " + selection);
            String originHash = null;
            try {
                originHash = genHash(selection);
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
            //check if the key belongs in my space- if so query the database and return the cursor
            if (checkIfKeyBelongsToMe(originHash)) {
                //query the database and return cursor
                deletedRows = sqlDB.delete(DBHelper.TABLE_NAME,"key=?",new String[]{selection});
                return deletedRows;
            } else {
                //else forward the request to the next node
               // synchronized (syncList) {
                    Log.d(TAG, "DELETE: DELETE KEY:  FORWARD");
                    Message deleteForward = new Message(myPortID, selection + ":" + myPortID, null, myPortID, "DELETE");
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, deleteForward.toString(), successor);

            }
        } else {
            //forwarded request for key
            //check if belongs to you - if so, return cursor else forward the request to the successor
            Log.d(TAG, "DELETE: DELETE SELECTION IS: " + selection);
            String key = selection.split(":")[0];
            String[] selectArgs = {key};
            String originPort = selection.split(":")[1];
            String originHash = null;
            try {
                originHash = genHash(key);
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }

            if (checkIfKeyBelongsToMe(originHash)) {
                Log.d(TAG, "DELETE: DELETE MY KEY SPACE");
                deletedRows = sqlDB.delete(DBHelper.TABLE_NAME,"key=?",new String[]{selection});
                /*String response = null;

                Message queryResponse = new Message(originPort, selection, response, myPortID, "QUERY_RESPONSE");
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, queryResponse.toString(), originPort);*/

            } else {

                Log.d(TAG, "DELETE: DELETE FORWARD");
                Message queryForward = new Message(originPort, selection, null, myPortID, "DELETE");
                new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, queryForward.toString(), successor);
            }
        }
        //other options to be added
        return deletedRows;
    }

    @Override
    public String getType(Uri uri) {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        sqlDB=dbHelper.getWritableDatabase();
        //int originPort = Integer.parseInt(msgValues[0])/2;
        String originHash= null,predHash=null,succHash=null;
        try {
            originHash = genHash(String.valueOf(values.get("key")));
            int predPort = Integer.parseInt(predecessor)/2;
            predHash=genHash(String.valueOf(predPort));
            int succPort = Integer.parseInt(successor)/2;
            succHash=genHash(String.valueOf(succPort));
            Log.d(TAG,"INSERT: PortID: "+myPortID+"#SUCCESSOR: "+successor+"#PREDECESSOR: "+predecessor);
            Log.d(TAG,"INSERT: Key is: "+String.valueOf(values.get("key"))+" and hash is: "+originHash);
            /*Log.d(TAG, "INSERT: pred and mine"+predHash.compareTo(myHash));
            Log.d(TAG, "INSERT: pred and key"+predHash.compareTo(originHash));
            Log.d(TAG, "INSERT: succ and mine"+succHash.compareTo(myHash));
            Log.d(TAG, "INSERT: succ and origin"+succHash.compareTo(originHash));
            Log.d(TAG, "INSERT: mine and origin"+myHash.compareTo(originHash));*/
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        Log.d(TAG,"INSERT: My hash: "+myHash+"\nPredecessor Hash: "+predHash+"\nSucessor Hash: "+succHash+"\nKey Hash: "+originHash);

        long id=0;

        //check if only one avd, then only one insert
        if(checkIfKeyBelongsToMe(originHash)){
            Log.d(TAG,"INSERT: MY KEY SPACE");
            try {
                id = sqlDB.insertWithOnConflict(DBHelper.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
            catch(Exception e){
                e.printStackTrace();
            }
        }
        else{
            //forward to your successor
            Log.d(TAG,"INSERT: FORWARD");
            Message msg=new Message(myPortID,values.get("key").toString(),values.get("value").toString(),myPortID,"INSERT");
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,msg.toString(),successor);
           // return null;
        }
        //other options to be added
        Uri newUri= ContentUris.withAppendedId(uri,id);
        //how to update after inserting????
        Log.v("insert", values.toString());
        return newUri;
    }


    //cannot call network connections on UI thread- need async tasks
    //Referrence:https://stackoverflow.com/questions/19740604/how-to-fix-networkonmainthreadexception-in-android
    //https://developer.android.com/reference/android/os/NetworkOnMainThreadException.html
    @Override
    public boolean onCreate() {
        // TODO Auto-generated method stub

        mUri = buildUri("content", "edu.buffalo.cse.cse486586.simpledht.provider");
        dbHelper=new DBHelper(getContext());

        /*
        * The code below for server task and Telephony Manager from PA1
        *
        * */
        // TelephonyManager tel = (TelephonyManager) this.getSystemService(Context.TELEPHONY_SERVICE);
        TelephonyManager tel = (TelephonyManager)getContext().getSystemService(Context.TELEPHONY_SERVICE);
        String portStr = tel.getLine1Number().substring(tel.getLine1Number().length() - 4);
        final String myPort = String.valueOf((Integer.parseInt(portStr) * 2));
        myPortID=myPort;
        try {
            myHash=genHash(portStr);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        Log.d(TAG, "My port is: "+myPort);
        try {
            myNodeID=genHash(portStr);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        //starting server task
        try {
            ServerSocket serverSocket = new ServerSocket(SERVER_PORT);
            Log.d(TAG, " in server local socket address is " + serverSocket.getLocalSocketAddress());
            new ServerTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, serverSocket);
        } catch (IOException e) {
            Log.e(TAG, "Can't create a ServerSocket");
        }

        // If I am not 5554, send a join request to 5554
        if(!portStr.equals("5554")){
            Message m = new Message(myPort,null,null,myPort,"JOIN");
            predecessor=myPort;
            successor=myPort;
            //send to 5554 through socket
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, m.toString(), startPortID);
        }
        else{ // I am 5554 do nothing
            predecessor=myPort;
            successor=myPort;
        }

        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        // TODO Auto-generated method stub

        Log.v("query", selection);
        MatrixCursor matrixCursor=new MatrixCursor(colNames);
        sqlDB=dbHelper.getWritableDatabase();

        SQLiteQueryBuilder sqLiteQueryBuilder=new SQLiteQueryBuilder();
        sqLiteQueryBuilder.setTables(DBHelper.TABLE_NAME);
        String [] mSelectionArgs={selection};

            //Cursor cursor=sqLiteQueryBuilder.query(sqlDB,projection,selection, selectionArgs,null,null, sortOrder);
            Cursor cursor = null;
            //check if only one avd - then all delete will be from here
            if (predecessor == myPortID && successor == myPortID) {
                if (selection.equals("*") || selection.equals("@")) {
                    //query all from the avd
                    cursor = sqLiteQueryBuilder.query(sqlDB, projection, null, null, null, null, sortOrder);
                } else {
                    cursor = sqLiteQueryBuilder.query(sqlDB, projection, "key = ?", mSelectionArgs, null, null, sortOrder);
                }
                return cursor;
            } else if (selection.equals("@")) {
                cursor = sqLiteQueryBuilder.query(sqlDB, projection, null, null, null, null, sortOrder);
                Log.d(TAG, "QUERY: @ CURSOR IS: " + cursor.toString());
                return cursor;
            } else if (selection.equals("*")) {
                Log.d(TAG, "QUERY: QUERY ALL: I AM THE ORIGIN");
                synchronized (syncList) {
                    //return mine and forward query to successor
                    cursor = sqLiteQueryBuilder.query(sqlDB, projection, null, null, null, null, sortOrder);
                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            Object[] columnValues = new Object[2];
                            columnValues[0] = cursor.getString(cursor.getColumnIndex("key"));
                            columnValues[1] = cursor.getString(cursor.getColumnIndex("value"));
                            matrixCursor.addRow(columnValues);
                        }
                    }
                    Message queryForward = new Message(myPortID, "*:" + myPortID, null, myPortID, "QUERY");
                    //send to successor
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, queryForward.toString(), successor);
                    while (syncList.isEmpty() || !receivedAll) {
                        try {
                            syncList.wait();
                        } catch (InterruptedException e) {
                            e.printStackTrace();
                        }
                    }
                }
                    for (String s : syncList) {
                        Object[] objectValues = new Object[2];
                        if(!s.equals(null) && s.contains("-")) {
                            objectValues[0] = s.split("-")[0]; //key
                            objectValues[1] = s.split("-")[1]; //value
                            matrixCursor.addRow(objectValues);
                        }
                        else{
                            Log.d(TAG,"RECEIVED NULL");
                        }
                    }
                    syncList.clear();
                //return the matrix cursor
                return matrixCursor;
            } else if (selection.contains("*")) {
                if (selection.split(":")[1].equals(myPortID)) {
                    //ring complete
                    Log.d(TAG, "QUERY: QUERY ALL: RING COMPLETE");
                    synchronized (syncList){
                        receivedAll = true;
                        syncList.notify();
                    }
                    //copy from list to cursor and return cursor
                    //return cursor
                } else {
                    //request forwarded from some node
                    Log.d(TAG, "QUERY: RECEIVED QUERY ALL");
                    cursor = sqLiteQueryBuilder.query(sqlDB, projection, null, null, null, null, sortOrder);
                    String response = "";
                    if (cursor != null) {
                        while (cursor.moveToNext()) {
                            response = response+cursor.getString(cursor.getColumnIndex("key")) + "-" + cursor.getString(cursor.getColumnIndex("value")) + ",";
                        }
                    }
                    else{
                        response="NO DATA";
                    }
                    String originPort = selection.split(":")[1];
                    Message queryResponse = new Message(originPort, selection, response, myPortID, "QUERY_RESPONSE");
                    //send to origin the cursor
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, queryResponse.toString(), originPort);

                    Message queryForward = new Message(originPort, selection, null, myPortID, "QUERY");
                    //send to successor the request
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, queryForward.toString(), successor);
                }
            } else if (!selection.contains(":")) {
                //has a key
                Log.d(TAG, "QUERY: QUERY SELECTION IS: " + selection);
                String originHash = null;
                try {
                    originHash = genHash(selection);
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }
                //check if the key belongs in my space- if so query the database and return the cursor
                if (checkIfKeyBelongsToMe(originHash)) {
                    //query the database and return cursor
                    cursor = sqLiteQueryBuilder.query(sqlDB, projection, "key = ?", mSelectionArgs, null, null, sortOrder);
                    return cursor;
                } else {
                    //else forward the request to the next node
                    synchronized (syncList) {
                        Log.d(TAG, "QUERY: QUERY FORWARD");
                        Message queryForward = new Message(myPortID, selection + ":" + myPortID, null, myPortID, "QUERY");
                        new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, queryForward.toString(), successor);
                        while (syncList.isEmpty()) {
                            try {
                                syncList.wait();
                            } catch (InterruptedException e) {
                                e.printStackTrace();
                            }
                        }
                        Log.d(TAG, "RESPONSE: SIZE IS: " + syncList.size());
                    }
                        for (String s : syncList) {
                            Log.d(TAG, "LIST ITEM: " + s);
                            Object[] objectValues = new Object[2];
                            if(s.contains("-")) {
                                objectValues[0] = s.split("-")[0]; //key
                                objectValues[1] = s.split("-")[1]; //value
                                matrixCursor.addRow(objectValues);
                            }
                        }
                        syncList.clear();
                   return matrixCursor;
                }
            } else {
                //forwarded request for key
                //check if belongs to you - if so, return cursor else forward the request to the successor
                Log.d(TAG, "QUERY: QUERY SELECTION IS: " + selection);
                String key = selection.split(":")[0];
                String[] selectArgs = {key};
                String originPort = selection.split(":")[1];
                String originHash = null;
                try {
                    originHash = genHash(key);
                } catch (NoSuchAlgorithmException e) {
                    e.printStackTrace();
                }

                if (checkIfKeyBelongsToMe(originHash)) {
                    Log.d(TAG, "QUERY: QUERY MY KEY SPACE");
                    cursor = sqLiteQueryBuilder.query(sqlDB, projection, "key = ?", selectArgs, null, null, sortOrder);
                    String response = null;
                    if (cursor != null && cursor.moveToFirst()) {
                        /*while (cursor.moveToNext()) {*/
                            response = cursor.getString(cursor.getColumnIndex("key")) + "-" + cursor.getString(cursor.getColumnIndex("value"));
                        //}
                    }
                    Message queryResponse = new Message(originPort, selection, response, myPortID, "QUERY_RESPONSE");
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, queryResponse.toString(), originPort);

                } else {
                    Log.d(TAG, "QUERY: QUERY FORWARD");
                    Message queryForward = new Message(originPort, selection, null, myPortID, "QUERY");
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, queryForward.toString(), successor);
                }
            }
            return cursor;
    }

    public boolean checkIfKeyBelongsToMe(String originHash){
        //boolean result=false;
        int predPort = Integer.parseInt(predecessor)/2;
        int succPort = Integer.parseInt(successor)/2;
        String predHash=null,succHash=null;
        try {
            predHash=genHash(String.valueOf(predPort));
            succHash=genHash(String.valueOf(succPort));
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        if(predecessor.equals(myPortID) && successor.equals(myPortID)){
            //only One AVD in the ring
            Log.d(TAG,"CHECK: ONLY ONE AVD");
            return true;
        } else if(predHash.compareTo(originHash)<=0 && originHash.compareTo(myHash)<0){
            //my key space
            Log.d(TAG,"CHECK: MY KEY SPACE");
            return true;
        }else if(predHash.compareTo(myHash)>0 && originHash.compareTo(predHash)>0 ){
            //insert first avd
            Log.d(TAG,"CHECK: FIRST AVD");
            return true;
        } else if(originHash.compareTo(predHash)<0 && originHash.compareTo(myHash)<0 && predHash.compareTo(myHash)>0){
            //small key
            Log.d(TAG,"CHECK: SMALLEST KEY");
            return true;
        }
        return false;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        return 0;
    }

    private String genHash(String input) throws NoSuchAlgorithmException {
        MessageDigest sha1 = MessageDigest.getInstance("SHA-1");
        byte[] sha1Hash = sha1.digest(input.getBytes());
        Formatter formatter = new Formatter();
        for (byte b : sha1Hash) {
            formatter.format("%02x", b);
        }
        return formatter.toString();
    }

    //from PA2A
    private Uri buildUri(String scheme, String authority) {
        Uri.Builder uriBuilder = new Uri.Builder();
        uriBuilder.authority(authority);
        uriBuilder.scheme(scheme);
        return uriBuilder.build();
    }
    private class ServerTask extends AsyncTask<ServerSocket, String, Void> {
        @Override
        protected Void doInBackground(ServerSocket... sockets) {
            ServerSocket serverSocket = sockets[0];

            while (true) {
                Log.d(TAG,"Waiting for incoming connections");
                try {
                    String msgReceived;
                    //Accepting client connection using accept() method
                    Socket client = serverSocket.accept();
                    //System.out.println(client.isConnected()+" client connected and "+client.getRemoteSocketAddress());
                    Log.d(TAG, " client connected" + client.getRemoteSocketAddress());
                    //Reading the message received by reading InputStream using BufferedReader
                    BufferedReader br = new BufferedReader(new InputStreamReader(client.getInputStream()));

                    OutputStream server_os = client.getOutputStream();
                    PrintWriter server_pw = new PrintWriter(server_os, true);

                    msgReceived = br.readLine();
                    Log.d(TAG, "Server Side: Message Received is  " + msgReceived);
                    if (msgReceived != null) {
                        String[] msgValues=msgReceived.split("#");
                        Message msg = new Message(msgValues[0],msgValues[1],msgValues[2],msgValues[3],msgValues[4]);

                        String msgType = msgValues[4];
                        Log.d(TAG,"ServerTask: Message Type is: "+msgType);
                        int originPort = Integer.parseInt(msgValues[0])/2;
                        String originHash=genHash(String.valueOf(originPort));

                        int predPort = Integer.parseInt(predecessor)/2;
                        String predHash=genHash(String.valueOf(predPort));

                        int succPort = Integer.parseInt(successor)/2;
                        String succHash=genHash(String.valueOf(succPort));

                        if(msgType.equals("JOIN")){

                            //Sending predecessor as key value, sucessor as sender value and data as value
                            Log.d(TAG,"JOIN Request at port: "+myPortID);
                            Log.d(TAG,"MSG RECEIVED: "+msgReceived);
                            //check if you have to handle
                            if((predHash.compareTo(myHash)>0 && originHash.compareTo(predHash)>0) || (originHash.compareTo(myHash)<0 && originHash.compareTo(predHash)<0 && predHash.compareTo(myHash)>0)){
                                Log.d(TAG,"FIRST AVD IN RING");
                                //you are the first or last avd??/
                                //you are the successor
                                //update your predecessor value and send message to pred and origin to update their successor and prredecessor
                                String old_predeccesor=predecessor;
                                msg.setType("UPDATE_NEIGHBOR");
                                //msg.setValue(old_predeccesor);
                                msg.setKey(old_predeccesor);

                                predecessor= msgValues[0];
                                msg.setSender(myPortID);

                                //fetch data now belonging to newAVD and delete from my storage
                                String response=deleteAndReturn(originHash);
                                //send it along with the message
                                msg.setValue(response);

                               // new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,msg.toString(),msgValues[0]);
                                Message msg2=new Message(msgValues[0],msgValues[1],msgValues[2],msgValues[3],"UPDATE_SUCCESSOR");
                                //new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,msg2.toString(),old_predeccesor);

                                //then send data to the new node after updating pointers
                                publishProgress(msg.toString(),msgValues[0],msg2.toString(),old_predeccesor);
                                //will have to query the content provider for data (new_pred<key<my_current) and then send over the same socket
                                //not sure about how this works

                            }
                            else if(predecessor.equals(myPortID) && myPortID.equals(successor)){
                                //or only AVD in the ring -
                                Log.d(TAG,"I AM FIRST: My PORT ID: "+myPortID);
                                predecessor=msgValues[0];
                                successor=msgValues[0];
                                String response=deleteAndReturn(originHash);
                                //msg.setValue(response);


                                Log.d(TAG,"Updated my neighbors: "+predecessor+" and "+successor);
                                Log.d(TAG,"Sending msg to new node");
                                Message msg2=new Message(msgValues[0],myPortID,response,myPortID,"UPDATE_NEIGHBOR");
                                publishProgress(msg2.toString(),msgValues[0],null,null);
                            }
                            else if(myHash.compareTo(originHash)>0 && originHash.compareTo(predHash)>=0){
                                Log.d(TAG,"NEW NODE IN MY SPACE");
                                //belongs in your key space
                                //you are the successor
                                String old_predeccesor=predecessor;
                                msg.setType("UPDATE_NEIGHBOR");
                                msg.setKey(old_predeccesor);

                                predecessor= msgValues[0];
                                msg.setSender(myPortID);

                                String response=deleteAndReturn(originHash);
                                msg.setValue(response);

                                //new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,msg.toString(),msgValues[0]);
                                Message msg2=new Message(msgValues[0],msgValues[1],msgValues[2],msgValues[3],"UPDATE_SUCCESSOR");
                                //new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,msg2.toString(),old_predeccesor);
                                Log.d(TAG,"Updated my neighbors: "+predecessor+" and "+successor);
                                publishProgress(msg.toString(),msgValues[0],msg2.toString(),old_predeccesor);

                                //then send data to the new node after updating pointers

                                //will have to query the content provider for data (new_pred<key<my_current) and then send over the same socket
                                //not sure about how this works

                            }
                            else {
                                //forward this request to your successor
                                Log.d(TAG,"JOIN FORWARD");
                                //new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,msg.toString(),successor);
                                Log.d(TAG,"My neighbors: "+predecessor+" and "+successor);
                                publishProgress(msg.toString(),successor,null,null);
                            }

                        }

                        else if(msgType.equals("UPDATE_NEIGHBOR")){
                            Log.d(TAG,"ServerTask:UPDATE_NEIGHBOR");

                            //updating predecessor and successor
                            //yet to handle distribution of data
                            predecessor=msg.getKey();
                            successor=msg.getSender();
                            Log.d(TAG,"Updated my neighbors: "+predecessor+" and "+successor);
                            String response=msg.getValue();
                            Log.d(TAG,"JOIN: DATA RECEIVED IS: "+response);
                            String[] keyValuePairs=response.split(",");
                            for(String s:keyValuePairs){
                                if(s.contains("-")){
                                    ContentValues mContentValues = new ContentValues();
                                    mContentValues.put("key", s.split("-")[0]);
                                    mContentValues.put("value", s.split("-")[1]);
                                    insert(mUri,mContentValues);
                                    Log.d(TAG,"JOIN: INSERT IN NEW AVD inserted");
                                }
                            }

                        }
                        else if(msgType.equals("UPDATE_SUCCESSOR")){
                            Log.d(TAG,"ServerTask:UPDATE_SUCCESSOR");
                            successor=msg.getOrigin();
                            Log.d(TAG,"Updated my successor: "+predecessor+" and "+successor);
                        }
                        else if(msgType.equals("QUERY")){
                            Log.d(TAG,"ServerTask:QUERY");
                            Cursor cursor=query( mUri, null, msgValues[1], null, null);
                            if(cursor!=null)
                            while(cursor.moveToNext()){
                                Log.d(TAG, "QUERY: CURSOR: "+cursor.toString());
                            }
                        }
                        else if(msgType.equals("QUERY_RESPONSE")){
                            String msgResponse=msgValues[2];
                            Log.d(TAG,"QUERYY_RESPONSE: Message received is: "+msgResponse);
                            String key=msgValues[1];
                            if(key.contains("*")){
                                //response for query all
                                String [] pairs=msgResponse.split(",");
                                synchronized (syncList){
                                    for(String s:pairs){
                                        syncList.add(s);

                                    }
                                    Log.d(TAG,"QUERY_RESPONSE: SYNCHRONIZED LIST");
                                    //Log.d(TAG,syncList.get(0));
                                    syncList.notify();
                                }
                                                            }
                            else{
                                String [] pairs=msgResponse.split(",");
                                synchronized (syncList){
                                    for(String s:pairs){
                                        syncList.add(s);
                                    }
                                    Log.d(TAG,"QUERY_RESPONSE: SYNCHRONIZED LIST");
                                    Log.d(TAG,syncList.get(0));

                                    syncList.notify();
                                }
                            }
                        }
                        else if(msgType.equals("INSERT")){
                            Log.d(TAG,"ServerTask:INSERT");
                            ContentValues mContentValues = new ContentValues();
                            mContentValues.put("key", msg.getKey());
                            mContentValues.put("value", msg.getValue());
                            insert(mUri,mContentValues);
                            Log.d(TAG,"ServerTask:INSERT    inserted");
                        }
                        else if(msgType.equals("DELETE")){
                            Log.d(TAG,"ServerTask:DELETE");
                            int deletedRows=delete( mUri, msgValues[1],null);
                        }
                        else{
                            Log.d(TAG,"ServerTask:else");
                        }
                    }
                    client.close();
                } catch (Exception e){
                    e.printStackTrace();
                }
               // return null;
            } // end of while loop
        }  //end-doInBackground

        public String deleteAndReturn(String originHash){
            String response="";
            List<String> keyList=new ArrayList<String>();
            Cursor qCursor=query(mUri,null,"@",null,null);
            if(qCursor!=null){
                while(qCursor.moveToNext()){
                    String key=qCursor.getString(qCursor.getColumnIndex("key"));
                    String value=qCursor.getString(qCursor.getColumnIndex("value"));
                    try {
                        if(genHash(key).compareTo(originHash)<=0){
                            response=response+key+"-"+value+",";
                            keyList.add(key);
                        }
                    } catch (NoSuchAlgorithmException e) {
                        e.printStackTrace();
                    }
                }
            }
            for(String s:keyList){
                int deletedRows=delete(mUri,s,null);
            }


            return response;

        }

        protected void onProgressUpdate(String...strings) {
            /*
             * The following code displays what is received in doInBackground().
             */
                String msg1=strings[0];
                String port1=strings[1];
                String msg2=strings[2];
                String port2=strings[3];
                Log.d(TAG,"onProgressUpdate: Message Received is: "+msg1+"-"+port1+"-"+msg2+"-"+port2);

            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,msg1,port1);
            Log.d(TAG,"PROGRESS: Second message: "+(msg2==null));
                if(msg2!=null && port2!=null){
                    new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,msg2,port2);
                }


            return;
        }


        protected void callClientTask(String msg, String port){
            //creating async client tasks
           new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR,msg,port);
           return;
        }
    }

                private class ClientTask extends AsyncTask<String, Void, Void> {



        @Override
        protected Void doInBackground(String... msgs) {

            String msgToSend=msgs[0];
            String portToSend=msgs[1];

            Socket socket = new Socket();
            try {
                socket.connect(new InetSocketAddress(InetAddress.getByAddress(new byte[]{10, 0, 2, 2}),
                        Integer.parseInt(portToSend)));
                Log.d(TAG, "Client Side: "+socket.isConnected() + " and " + socket.getRemoteSocketAddress() + " and " + socket.getLocalSocketAddress());
                //Fetching the output stream of the socket.
                OutputStream os = socket.getOutputStream();
                PrintWriter pw = new PrintWriter(os, true);
                //Writing the message to be send to the other device on the socket's output stream.
                Log.d(TAG,"CLIENT TASK: Msg to send is: "+msgToSend);
                pw.println(msgToSend);
                pw.flush();

                //socket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }


            return null;
        }
    }

}


