package edu.buffalo.cse.cse486586.simpledht;

import java.io.IOException;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Formatter;

import android.content.ContentProvider;
import android.content.ContentUris;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
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
    String startPortID = "11108";


    SQLiteDatabase sqlDB;

    ContentValues contentValues;


    @Override
    public int delete(Uri uri, String selection, String[] selectionArgs) {
        // TODO Auto-generated method stub
        sqlDB=dbHelper.getWritableDatabase();
        int deletedRows=0;
        //check if only one avd - then all delete will be from here
        if(predecessor==myPortID && successor==myPortID){
            if(selection.equals("*") || selection.equals("@")){
                //delete all from the avd
                deletedRows = sqlDB.delete(DBHelper.TABLE_NAME, null, null);
            }
            else {
                deletedRows = sqlDB.delete(DBHelper.TABLE_NAME,"key=?",new String[]{selection});
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
        long id=0;
        //check if only one avd, then only one insert
        if(predecessor==myPortID && successor==myPortID){
            //instead of insert,using insertWithOnConflict to be able to handle conflicts.
            try {
                id = sqlDB.insertWithOnConflict(DBHelper.TABLE_NAME, null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
            catch(Exception e){
                e.printStackTrace();
            }
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
        Log.d(TAG, "My port is: "+myPort);
        try {
            myNodeID=genHash(portStr);
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        // If I am not 5554, send a join request to 5554
        if(!portStr.equals("5554")){
            Message m = new Message(myPort,null,null,myPort,"JOIN");


            predecessor=myPort;
            successor=myPort;
            //send to 5554 through socket
            new ClientTask().executeOnExecutor(AsyncTask.SERIAL_EXECUTOR, m.toString(), startPortID);



        }

        return false;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] selectionArgs,
            String sortOrder) {
        // TODO Auto-generated method stub
        sqlDB=dbHelper.getWritableDatabase();

        SQLiteQueryBuilder sqLiteQueryBuilder=new SQLiteQueryBuilder();
        sqLiteQueryBuilder.setTables(DBHelper.TABLE_NAME);
        String [] mSelectionArgs={selection};

        //Cursor cursor=sqLiteQueryBuilder.query(sqlDB,projection,selection, selectionArgs,null,null, sortOrder);
        Cursor cursor=null;

        //check if only one avd - then all delete will be from here
        if(predecessor==myPortID && successor==myPortID){
            if(selection.equals("*") || selection.equals("@")){
                //delete all from the avd
               cursor =sqLiteQueryBuilder.query(sqlDB,projection,null,null,null,null,sortOrder);
            }
            else {
                cursor=sqLiteQueryBuilder.query(sqlDB,projection,"key = ?",mSelectionArgs,null,null,sortOrder);
            }
        }
        //other options to be added

        Log.v("query", selection);
        return cursor;
        //return null;
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


            return null;
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
                        Integer.parseInt("11108")),500)  ;
                Log.d(TAG, "Client Side: "+socket.isConnected() + " and " + socket.getRemoteSocketAddress() + " and " + socket.getLocalSocketAddress());
                //Fetching the output stream of the socket.
                OutputStream os = socket.getOutputStream();
                PrintWriter pw = new PrintWriter(os, true);
                //Writing the message to be send to the other device on the socket's output stream.
                System.out.println(msgToSend);
                pw.println(msgToSend);
                pw.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }

            return null;
        }
    }

}


