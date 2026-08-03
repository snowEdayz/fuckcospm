package com.fuckcospm;

import android.app.Activity;
import android.os.Bundle;
import android.widget.TextView;

public class MainActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        TextView text = new TextView(this);
        text.setPadding(32, 32, 32, 32);
        text.setTextSize(14);
        text.setText(
                "FuckCosPM (LSPosed 模块)\n\n" +
                        "作用域: system_server\n\n" +
                        "功能: 拦截 OPPO 系统服务 putActivityStartWhiteList，" +
                        "从 Activity 启动白名单 (src_pkg / dst_pkg / src_and_dst) 中移除:\n" +
                        "  - com.eg.android.AlipayGphone (支付宝)\n" +
                        "  - com.heytap.market (应用市场)\n\n" +
                        "启用后在 LSPosed 中勾选 system framework，重启生效。");
        setContentView(text);
    }
}
