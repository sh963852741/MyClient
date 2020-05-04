package com.example.myclient;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import androidx.appcompat.app.AppCompatActivity;

import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.Socket;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import droidninja.filepicker.FilePickerBuilder;
import droidninja.filepicker.FilePickerConst;
import droidninja.filepicker.utils.ContentUriUtils;

public class MainActivity extends AppCompatActivity
{

    // 主线程Handler
    // 用于将从服务器获取的消息显示出来
    private Handler mMainHandler;

    ArrayList<Uri> filePaths=new ArrayList<Uri>();
    // Socket变量
    private Socket socket;

    // 线程池
    // 为了方便展示,此处直接采用线程池进行线程管理,而没有一个个开线程
    private ExecutorService mThreadPool;

    // 连接 断开连接 发送数据到服务器 的按钮变量
    private Button btnSend,btnSelect;


    // 输入需要发送的消息 输入框
    private EditText mEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化所有按钮
        btnSend = (Button) findViewById(R.id.send);
        btnSelect = (Button) findViewById(R.id.Select);

        // 初始化线程池
        mThreadPool = Executors.newCachedThreadPool();


        /**
         * 接收 服务器消息
         */
        btnSelect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                SelectFile();
            }
        });
        /**
         * 发送消息 给 服务器
         */
        btnSend.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                for (final Uri uri:filePaths)
                {
                    // 利用线程池直接开启一个线程 & 执行该线程
                    mThreadPool.execute(new Runnable() {
                        @Override
                        public void run() {
                            try {
                                SendFile(uri);
                            } catch (IOException | URISyntaxException e) {
                                e.printStackTrace();
                            }
                        }
                    });
                }
            }
        });

    }
    public void SelectFile()
    {
        ArrayList<Uri> filePaths=new ArrayList<Uri>();
        FilePickerBuilder.getInstance().setMaxCount(10)
                .setSelectedFiles(filePaths)
                .pickPhoto(this);
    }

    public void SendFile(Uri uri) throws IOException, URISyntaxException {
        OutputStream outputStream = null;
        InputStream intputStream = null;
        Socket socket=null;
        try {
            // 创建Socket对象 & 指定服务端的IP 及 端口号
            socket = new Socket("192.168.18.3", 8888);
            outputStream = socket.getOutputStream();
            intputStream = socket.getInputStream();
        } catch (IOException e) {
            e.printStackTrace();
        }
        String path = ContentUriUtils.INSTANCE.getFilePath(this,uri);
        byte[] recvBuffer = new byte[1024];
        byte[] sendBuffer = new byte[1024];
        // 发送文件信息
        FileInputStream in = new FileInputStream(path);
        sendBuffer=intToByteArray(3,sendBuffer,0);
        sendBuffer=intToByteArray(in.available(),sendBuffer,4);
        byte[] fileNameBytes=path.getBytes();
        for(int i=0;i<fileNameBytes.length;++i)
        {
            sendBuffer[i+12]=fileNameBytes[i];
        }
        outputStream.write(sendBuffer);
        //调整文件指针
        intputStream.read(recvBuffer);
        int pos=byteArrayToInt(recvBuffer);
        in.skip(pos);
        //开始传输文件
        int byteRead = 0;
        while ((byteRead = in.read(sendBuffer)) != -1) {
            outputStream.write(sendBuffer);
        }
        in.close();
        //校验文件
        intputStream.read(recvBuffer);
        outputStream.write("finish\0".getBytes());
        intputStream.close();
        outputStream.close();
        socket.close();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        switch (requestCode) {
            case FilePickerConst.REQUEST_CODE_PHOTO:
                if (resultCode == Activity.RESULT_OK && data != null) {
                    ArrayList<Uri> dataList = data.getParcelableArrayListExtra(FilePickerConst.KEY_SELECTED_MEDIA);
                    if (dataList != null) {
                        filePaths.addAll(dataList);
                    }
                }
                break;

            case FilePickerConst.REQUEST_CODE_DOC:
                if (resultCode == Activity.RESULT_OK && data != null) {
                    ArrayList<Uri> dataList = data.getParcelableArrayListExtra(FilePickerConst.KEY_SELECTED_DOCS);
                    if (dataList != null) {
                        filePaths.addAll(dataList);
                    }
                }
                break;
        }
        // addThemToView(photoPaths, docPaths);
    }

    private byte[] intToByteArray(int value,byte[] intput, int fromWhere)
    {
        intput[fromWhere+0] = (byte) (value & 0xFF);
        intput[fromWhere+1] = (byte) (value >> 8  & 0xFF);
        intput[fromWhere+2] = (byte) (value >> 16 & 0xFF);
        intput[fromWhere+3] = (byte) (value >> 24 & 0xFF);
        return intput;
    }

    private int byteArrayToInt(byte[] byteArray)
    {
        if(byteArray.length != 4)
        {
            return 0;
        }
        int value = byteArray[0] & 0xFF;
        value |= byteArray[1] << 8;
        value |= byteArray[2] << 16;
        value |= byteArray[3] << 24;
        return value;
    }
}

