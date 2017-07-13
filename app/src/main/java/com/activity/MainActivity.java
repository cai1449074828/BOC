package com.activity;

import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Environment;
import android.os.Looper;
import android.provider.MediaStore;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AlertDialog;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import com.Bean.Device;
import com.activity.QRActivity.ScanActivity;
import com.adapter.MyRecyclerViewAdapter;
import com.adapter.MyRecyclerViewItemClickListener;
import com.amap.api.services.core.PoiItem;
import com.amap.api.services.poisearch.PoiResult;
import com.amap.api.services.poisearch.PoiSearch;
import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.utils.ImageUtils;
import com.zzc.R;
import com.utils.CommonUtil;
import com.zhy.http.okhttp.OkHttpUtils;
import com.zhy.http.okhttp.callback.StringCallback;

import java.util.ArrayList;
import java.util.List;

import butterknife.BindView;
import butterknife.ButterKnife;
import butterknife.OnClick;
import cn.bingoogolapple.qrcode.zxing.QRCodeDecoder;
import cn.pedant.SweetAlert.SweetAlertDialog;
import okhttp3.Call;

public class MainActivity extends AppCompatActivity implements PoiSearch.OnPoiSearchListener,MyRecyclerViewItemClickListener{

    @BindView(R.id.openQrCodeScan)
    Button openQrCodeScan;
    @BindView(R.id.name)
    EditText editText_name;
    @BindView(R.id.textView_info)
    TextView textView_info;
    private static final int RESULT_OK_QR = 0xA1;
    //打开扫描界面请求码
    private static final int REQUESTCODE_QR = 0x01;
    //打开GPS定位界面请求码
    private static final int REQUESTCODE_GPS = 0x02;
    private static final int REQUESTCODE_Album = 0x03;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ButterKnife.bind(this);
        initDialog();
        initView();
        getAll();
        showCodeDialog("F07735361500078");//F07735361500078
    }
    @BindView(R.id.textView_city)
    TextView textView_city;
    @BindView(R.id.textView_GPS)
    TextView textView_GPS;
    @BindView(R.id.textView_GPS_error)
    TextView textView_GPS_error;
    private void initView() {
        initInfo_GPS(getIntent().getStringExtra("city"),getIntent().getStringExtra("minDistance_name"));
        textView_GPS_error.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                Intent intent=new Intent(MainActivity.this,GPSActivity.class);
                intent.putExtra("isFirst",false);
                startActivityForResult(intent, REQUESTCODE_GPS);
            }
        });
        initRecycleView();
        editText_name.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {

            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                filterByName();
            }

            @Override
            public void afterTextChanged(Editable s) {

            }
        });
    }
    private void initInfo_GPS(String city, String minDistance_name) {
        textView_city.setText(city);
        textView_GPS.setText(minDistance_name);
    }
    @BindView(R.id.recyclerView)
    RecyclerView mRecyclerView;
    private ArrayList<Device> list_recycle = new ArrayList<>();
    private ArrayList<Device> list_all = new ArrayList<>();
    private MyRecyclerViewAdapter adapter;

    private void initRecycleView() {
        adapter = new MyRecyclerViewAdapter(this, list_recycle);
        mRecyclerView.setLayoutManager(new LinearLayoutManager(this));
        //自动滑动到最低,更新List时需要写一次
        mRecyclerView.setAdapter(adapter);
        //adapter.notifyDataSetChanged();
        //mRecyclerView.smoothScrollToPosition(adapter.getItemCount());
    }
    private void initDialog() {
        initDeviceDialog();
        initCodeDialog();
        initWaitDialog();
    }
    private View view_deviceDialog;
    private AlertDialog alertDeviceDialog;
    private void initDeviceDialog() {
        view_deviceDialog= this.getLayoutInflater().inflate(R.layout.dialog_device,null);
        alertDeviceDialog=new AlertDialog.Builder(MainActivity.this)
                .setView(view_deviceDialog)
                .setPositiveButton("确定", null)
                .create();
    }
    private View view_codeDialog;
    private AlertDialog alertcodeDialog;
    private void initCodeDialog() {
        view_codeDialog= getLayoutInflater().inflate(R.layout.dialog_code,null);
        alertcodeDialog=new AlertDialog.Builder(MainActivity.this)
                .setTitle("是否识别")
                .setView(view_codeDialog)
                .setPositiveButton("确定", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface anInterface, int i) {
                        sweetAlertDialog_Wait.show();
                        get_Device(device_kjlabel);
                    }
                })
                .setNegativeButton("取消",null)
                .create();
    }
    private Intent intent;
    @OnClick({R.id.openQrCodeScan,R.id.textView_GPS_error,R.id.textView_city})
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.openQrCodeScan: {
                new AlertDialog.Builder(MainActivity.this).setItems(new String[]{"从相册选取", "摄像头扫描"}, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        switch (which){
                            case 0:{
                                intent = new Intent(Intent.ACTION_PICK);
                                intent.setDataAndType(MediaStore.Images.Media.INTERNAL_CONTENT_URI, "image/*");
                                startActivityForResult(intent, REQUESTCODE_Album);
                                break;
                            }
                            case 1:{
                                //打开二维码扫描界面
                                if (CommonUtil.isCameraCanUse()) {
                                    startActivityForResult(new Intent(MainActivity.this, ScanActivity.class), REQUESTCODE_QR);
//                                    startActivityForResult(new Intent(MainActivity.this, ZbarScannerActivity.class), REQUESTCODE_QR);
                                } else {
                                    Toast.makeText(MainActivity.this, "请打开此应用的摄像头权限！", Toast.LENGTH_SHORT).show();
                                }
                            }
                        }
                    }
                }).show();
                break;
            }
            case R.id.textView_GPS_error:{
                Intent intent=new Intent(MainActivity.this,GPSActivity.class);
                intent.putExtra("isFirst",false);
                startActivityForResult(intent, REQUESTCODE_GPS);
                break;
            }
            case R.id.textView_city:{
                break;
            }
        }
    }
    private SweetAlertDialog sweetAlertDialog_Wait;
    private void initWaitDialog() {
        sweetAlertDialog_Wait=new SweetAlertDialog(this,SweetAlertDialog.PROGRESS_TYPE).setTitleText("正在查询");
    }
    private void getAll() {
        OkHttpUtils
                .post()
                .url("http://s14i594712.iask.in:40293/Action/Action_DeviceAction_getAll")
                .build()
                .execute(new StringCallback()
                {
                    @Override
                    public void onError(Call call, Exception e, int id) {
                        textView_info.setText("查询数据失败,正在重新查询数据.....");
                        Toast.makeText(MainActivity.this, "查询数据失败了，重新查询", Toast.LENGTH_SHORT).show();
                        try {
                            Thread.sleep(1000);
                            getAll();
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                        }
                    }
                    @Override
                    public void onResponse(final String response, int id) {
                        System.out.println(response);
                        new Thread(new Runnable() {
                            @Override
                            public void run() {
                                //由于加载过长，放入线程
                                list_all=new Gson().fromJson(response,new TypeToken<List<Device>>() {}.getType());
                                filterByName();
                            }
                        }).start();
                    }
                });
    }
    private void filterByName() {
        if (list_all.size()>0) {
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    textView_info.setText("查询数据成功，正在过滤数据.....");
                }
            });
            list_recycle.clear();
            String name = editText_name.getText().toString();
            System.out.println("当前名字是" + name);
            for (Device device : list_all) {
                if (device.getDevice_own() != null && device.getDevice_own().contains(name)) {
                    list_recycle.add(device);
                    System.out.println(device.getDevice_own() + "被包含---------------------------------------------------------");
                } else {
                    System.out.println(device.getDevice_own() + "没有包含" + name);
                }
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    textView_info.setText("SUCCESS!");
                    adapter.notifyDataSetChanged();
                }
            });
        }
    }
    private void get_Device(final String device_kjlabel){
        OkHttpUtils
                .post()
                .url("http://s14i594712.iask.in:40293/Action/Action_DeviceAction_getDevice")
                .addParams("device_kjlabel", device_kjlabel)
                .build()
                .execute(new StringCallback()
                {
                    @Override
                    public void onError(Call call, Exception e, int id) {
                        Toast.makeText(MainActivity.this, "查询二维码失败了,再次查询", Toast.LENGTH_SHORT).show();
                        try {
                            Thread.sleep(1000);
                            get_Device(device_kjlabel);
                        } catch (InterruptedException e1) {
                            e1.printStackTrace();
                        }
                    }
                    @Override
                    public void onResponse(String response, int id) {
                        sweetAlertDialog_Wait.dismiss();
                        System.out.println(response);
                        if (!(response.equals("")||response.equals("null"))) {
                            Device device = new Gson().fromJson(response, Device.class);
                            showDeviceDialog(device);
                        }
                        else {
                            new SweetAlertDialog(MainActivity.this, SweetAlertDialog.WARNING_TYPE).setTitleText("警告").setContentText("没有查询到数据").show();
                        }
                    }
                });
    }
    private String device_kjlabel;
    private void showCodeDialog(String device_kjlabel){
        this.device_kjlabel=device_kjlabel;
        ((TextView)view_codeDialog.findViewById(R.id.textView_device_kjlabel_context)).setText(device_kjlabel);
        alertcodeDialog.show();
    }
    private void showDeviceDialog(Device device) {
        ((TextView) view_deviceDialog.findViewById(R.id.textView_ID_context)).setText(String.valueOf(device.getID()));
        ((TextView) view_deviceDialog.findViewById(R.id.textView_device_class_context)).setText(device.getDevice_class());
        ((TextView) view_deviceDialog.findViewById(R.id.textView_device_type_context)).setText(device.getDevice_type());
        ((TextView) view_deviceDialog.findViewById(R.id.textView_device_kjlabel_context)).setText(device.getDevice_kjlabel());
        ((TextView) view_deviceDialog.findViewById(R.id.textView_device_label_context)).setText(device.getDevice_label());
        ((TextView) view_deviceDialog.findViewById(R.id.textView_device_seq_context)).setText(device.getDevice_seq());
        ((TextView) view_deviceDialog.findViewById(R.id.textView_device_address_context)).setText(device.getDevice_address());
        ((TextView) view_deviceDialog.findViewById(R.id.textView_device_own_context)).setText(device.getDevice_own());
        ((TextView) view_deviceDialog.findViewById(R.id.textView_device_use_context)).setText(device.getDevice_use());
        ((TextView) view_deviceDialog.findViewById(R.id.textView_device_wd_context)).setText(device.getDevice_wd());
        ((TextView) view_deviceDialog.findViewById(R.id.textView_device_wd_key_context)).setText(device.getDevice_wd_key());
        ((TextView) view_deviceDialog.findViewById(R.id.textView_flag_context)).setText(String.valueOf(device.getCheck_flag()));
        ((TextView) view_deviceDialog.findViewById(R.id.textView_check_flag_context)).setText(String.valueOf(device.getCheck_flag()));
        ((TextView) view_deviceDialog.findViewById(R.id.textView_input_date_context)).setText(device.getInput_date());
        String name = editText_name.getText().toString();
        if (device.getDevice_own() != null && device.getDevice_own().contains(name)) {
            ((TextView) view_deviceDialog.findViewById(R.id.textView_isOwn)).setText("这个是你的设备");
            ((TextView) view_deviceDialog.findViewById(R.id.textView_isOwn)).setTextColor(Color.GREEN);
        } else {
            ((TextView) view_deviceDialog.findViewById(R.id.textView_isOwn)).setText("这个不是你的设备");
            ((TextView) view_deviceDialog.findViewById(R.id.textView_isOwn)).setTextColor(Color.RED);
        }
        alertDeviceDialog.show();
    }
    @Override
    public void OnItemClick(Device device) {
        showDeviceDialog(device);
    }
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        switch (requestCode) {
                case REQUESTCODE_GPS: {
                        switch (resultCode) {
                            case RESULT_OK: {
                                initInfo_GPS(intent.getStringExtra("city"), intent.getStringExtra("minDistance_name"));
                                break;
                            }
                        }
                        break;
                }
                case REQUESTCODE_QR: {
                    switch (resultCode) {
                        case RESULT_OK_QR: {
                            String device_kjlabel = intent.getStringExtra("device_kjlabel");
                            //将扫描出的信息显示出来
                            //device_kjlabel=F07735361500078
    //                      device_kjlabel="F07735361500078";
                            Toast.makeText(MainActivity.this, "识别二维码为\n" + device_kjlabel, Toast.LENGTH_SHORT).show();
                            System.out.println("识别二维码为" + device_kjlabel);
                            showCodeDialog(device_kjlabel);
                            break;
                        }
                    }
                    break;
                }
            case REQUESTCODE_Album:{
                System.out.println("开始扫描图片");
                final String QR_path=new ImageUtils(MainActivity.this).intentLoadToPath(intent);
                System.out.println("图片路径为"+QR_path);
//                final String file_path = new File(Environment.getExternalStorageDirectory(), "QR.jpg").getAbsolutePath();
                new AsyncTask<Void, Void, String>() {
                    @Override
                    protected String doInBackground(Void... params) {
                        return QRCodeDecoder.syncDecodeQRCode(QR_path);
                    }

                    @Override
                    protected void onPostExecute(String device_kjlabel) {
                        if (TextUtils.isEmpty(device_kjlabel)) {
                            Toast.makeText(MainActivity.this, "未发现二维码", Toast.LENGTH_SHORT).show();
                        } else {
                            Toast.makeText(MainActivity.this, device_kjlabel, Toast.LENGTH_SHORT).show();
                            showCodeDialog(device_kjlabel);
                        }
                    }
                }.execute();
                    break;
                }
        }
    }

    @Override
    public void onPoiSearched(PoiResult result, int i) {
        System.out.println("onPoiSearched");
        System.out.println("建议城市:"+result.getSearchSuggestionCitys());
        System.out.println("建议关键词:"+result.getSearchSuggestionKeywords());
        System.out.println("result.getPois().size:"+result.getPois().size());
        for (PoiItem poiItem:result.getPois()){
            //纬度
            double lat = poiItem.getLatLonPoint().getLatitude();
            //经度
            double lng = poiItem.getLatLonPoint().getLongitude();
            System.out.println("纬度:"+lat);
            System.out.println("经度:"+lng);
//            LocationUtils.getDistance()
        }
    }

    @Override
    public void onPoiItemSearched(PoiItem item, int i) {
        System.out.println("onPoiItemSearched");
    }


    long newTime;
    /**
     * 监听返回键
     */
    @Override
    public void onBackPressed() {
        if (System.currentTimeMillis() - newTime > 2000) {
            newTime = System.currentTimeMillis();
            Snackbar snackbar = Snackbar.make(mRecyclerView, "再按一次返回键退出程序", Snackbar.LENGTH_SHORT);
            snackbar.getView().setBackgroundColor(getResources().getColor(R.color.colorPrimary));
            snackbar.show();
            //Toast.makeText(this, "再按一次返回键退出程序", Toast.LENGTH_SHORT).show();
        } else {
            finish();
        }
    }
}
