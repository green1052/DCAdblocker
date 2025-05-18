package com.green1052.dcadblocker

import android.net.Uri
import android.webkit.WebResourceResponse
import android.webkit.WebView
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.ByteArrayInputStream

class MainHook : IXposedHookLoadPackage {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != "com.dcinside.app.android") return

        XposedHelpers.findAndHookMethod(
            "android.webkit.WebViewClient",
            lpparam.classLoader,
            "shouldInterceptRequest",
            WebView::class.java,
            "android.webkit.WebResourceRequest",
            object : XC_MethodHook() {
                @Throws(Throwable::class)
                override fun beforeHookedMethod(param: MethodHookParam) {
                    val uri = XposedHelpers.callMethod(param.args[1], "getUrl") as Uri

                    if (!uri.toString().contains("app.dcinside.com/api/_naverad")) return

                    param.setResult(
                        WebResourceResponse(
                            "text/plain",
                            "UTF-8",
                            ByteArrayInputStream("".toByteArray())
                        )
                    )
                }
            })
    }
}