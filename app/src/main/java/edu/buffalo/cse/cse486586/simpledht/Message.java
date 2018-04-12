package edu.buffalo.cse.cse486586.simpledht;

/**
 * Created by charushi on 4/6/18.
 */

public class Message {
    public String origin;
    public String key;
    public String value;
    public String hash;
    public String sender;
    public String type;
    public String response;

    public Message(){

    }

    public Message(String origin, String key, String value, String sender, String type) {
        this.origin = origin;
        this.key = key;
        this.value = value;
        this.sender = sender;
        this.type = type;
    }

    public String getOrigin() {
        return origin;
    }

    public void setOrigin(String origin) {
        this.origin = origin;
    }

    public String getKey() {
        return key;
    }

    public void setKey(String key) {
        this.key = key;
    }

    public String getValue() {
        return value;
    }

    public void setValue(String value) {
        this.value = value;
    }

    public String getHash() {
        return hash;
    }

    public void setHash(String hash) {
        this.hash = hash;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getResponse() {
        return response;
    }

    public void setResponse(String response) {
        this.response = response;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        Message message = (Message) o;

        if (origin != null ? !origin.equals(message.origin) : message.origin != null) return false;
        if (key != null ? !key.equals(message.key) : message.key != null) return false;
        if (value != null ? !value.equals(message.value) : message.value != null) return false;
        if (hash != null ? !hash.equals(message.hash) : message.hash != null) return false;
        if (sender != null ? !sender.equals(message.sender) : message.sender != null) return false;
        if (type != null ? !type.equals(message.type) : message.type != null) return false;
        return response != null ? response.equals(message.response) : message.response == null;
    }

    @Override
    public int hashCode() {
        int result = origin != null ? origin.hashCode() : 0;
        result = 31 * result + (key != null ? key.hashCode() : 0);
        result = 31 * result + (value != null ? value.hashCode() : 0);
        result = 31 * result + (hash != null ? hash.hashCode() : 0);
        result = 31 * result + (sender != null ? sender.hashCode() : 0);
        result = 31 * result + (type != null ? type.hashCode() : 0);
        result = 31 * result + (response != null ? response.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder(origin+"#"+key+"#"+value+"#"+sender+"#"+type);
        return sb.toString();
    }
}
