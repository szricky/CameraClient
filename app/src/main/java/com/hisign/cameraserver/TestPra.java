package com.hisign.cameraserver;

import android.os.Parcel;
import android.os.Parcelable;
import android.util.Log;

public class TestPra implements Parcelable {
    private static TestPra mTestPra;
    private String name;
    private byte[] mBytes;

    private static TestPra getInstance(Parcel in){
        if (mTestPra ==null){
            mTestPra = new TestPra(in);
        }
        return mTestPra;
    }



    public byte[] getBytes() {
        return mBytes;
    }

    public void setBytes(byte[] bytes) {
        mBytes = bytes;
    }


    public String getName() {
        return name;
    }


    public void setName(String name) {
        this.name = name;
    }


    public TestPra(){

    }


    protected TestPra(Parcel in) {
      //  Log.d(TAG,"readByteArray");
         /*  mBytes = new byte[in.readInt()];
        in.readByteArray(mBytes);*/
        Log.d(TAG,"createByteArray");
        name =  in.readString();
        mBytes = in.createByteArray();

    }

    private static final String TAG = "TestPra" ;
    public static final Creator<TestPra> CREATOR = new Creator<TestPra>() {
        @Override
        public TestPra createFromParcel(Parcel in) {
            Log.d(TAG,"createFromParcel");
            return new TestPra(in);//TestPra.getInstance(in);//new TestPra(in);
        }

        @Override
        public TestPra[] newArray(int size) {
            Log.d(TAG,"newArray");
            return new TestPra[size];
        }
    };

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(name);
        dest.writeByteArray(mBytes);
    }

    public void readFromParcel(Parcel in){
        name = in.readString();
        mBytes = in.createByteArray();
    }



    @Override
    public String toString() {
        return "TestPra{" +
                "name size ='" + name +
                '}';
    }


}
