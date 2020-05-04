package com.example.myclient;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import androidx.appcompat.app.AppCompatActivity;

import android.os.Parcelable;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Reader;
import java.net.Socket;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import droidninja.filepicker.FilePickerBuilder;
import droidninja.filepicker.FilePickerConst;
import droidninja.filepicker.utils.ContentUriUtils;

public class MainActivity extends AppCompatActivity
{
    /**
     * 主 变量
     */

    // 主线程Handler
    // 用于将从服务器获取的消息显示出来
    private Handler mMainHandler;

    ArrayList<Uri> filePaths=new ArrayList<Uri>();
    // Socket变量
    private Socket socket;

    // 线程池
    // 为了方便展示,此处直接采用线程池进行线程管理,而没有一个个开线程
    private ExecutorService mThreadPool;

    // 输入流读取器对象
    InputStreamReader isr ;
    BufferedReader br ;

    // 接收服务器发送过来的消息
    String response;


    /**
     * 按钮 变量
     */

    // 连接 断开连接 发送数据到服务器 的按钮变量
    private Button btnConnect, btnDisconnect, btnSend;

    // 显示接收服务器消息 按钮
    private TextView Receive,receive_message;

    // 输入需要发送的消息 输入框
    private EditText mEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // 初始化所有按钮
        btnConnect = (Button) findViewById(R.id.connect);
        btnDisconnect = (Button) findViewById(R.id.disconnect);
        btnSend = (Button) findViewById(R.id.send);
        Receive = (Button) findViewById(R.id.Receive);

        // 初始化线程池
        mThreadPool = Executors.newCachedThreadPool();

        // 实例化主线程,用于更新接收过来的消息
        mMainHandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                switch (msg.what) {
                    case 0:
                        receive_message.setText(response);
                        break;
                }
            }
        };

        /**
         * 创建客户端 & 服务器的连接
         */
        btnConnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // 利用线程池直接开启一个线程 & 执行该线程
                mThreadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        try
                        {
                            // 创建Socket对象 & 指定服务端的IP 及 端口号
                            socket = new Socket("192.168.18.3", 8888);
                            // 判断客户端和服务器是否连接成功
                            System.out.println(socket.isConnected());
                        }
                        catch (IOException e)
                        {
                            e.printStackTrace();
                        }
                    }
                });

            }
        });

        /**
         * 接收 服务器消息
         */
        Receive.setOnClickListener(new View.OnClickListener() {
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
                // 利用线程池直接开启一个线程 & 执行该线程
                mThreadPool.execute(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            SendFile();
                        } catch (IOException | URISyntaxException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        });

        /**
         * 断开客户端 & 服务器的连接
         */
        btnDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                try {

                    // 断开 服务器发送到客户端 的连接，即关闭输入流读取器对象BufferedReader
                    br.close();

                    // 最终关闭整个Socket连接
                    socket.close();

                    // 判断客户端和服务器是否已经断开连接
                    System.out.println(socket.isConnected());

                } catch (IOException e) {
                    e.printStackTrace();
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

    public void SendFile() throws IOException, URISyntaxException {
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
        String path = ContentUriUtils.INSTANCE.getFilePath(this,filePaths.get(0));
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
        //校验文件
        intputStream.read(recvBuffer);
        outputStream.write("finish\0".getBytes());
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

