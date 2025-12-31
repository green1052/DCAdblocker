package com.green1052.dcadblocker

import android.content.pm.PackageInfo
import android.content.pm.Signature
import android.content.res.XResources
import android.net.Uri
import android.util.TypedValue
import android.webkit.WebResourceResponse
import android.webkit.WebView
import de.robv.android.xposed.IXposedHookInitPackageResources
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.ByteArrayInputStream


private const val PACKAGE_NAME = "com.dcinside.app.android"
private const val DC_SIGNATURE = "E6:B2:71:44:A2:76:60:B1:E0:06:08:FA:45:D3:19:06:5D:97:D0:A7:1F:B5:4B:8C:A1:75:6E:83:46:EC:29:DD"

class MainHook : IXposedHookLoadPackage, IXposedHookInitPackageResources {
    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != PACKAGE_NAME) return

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

                    param.result = WebResourceResponse(
                        "text/plain", "UTF-8", ByteArrayInputStream("".toByteArray())
                    )
                }
            })

        XposedHelpers.findAndHookMethod(
            "android.content.pm.PackageManager",
            lpparam.classLoader,
            "getPackageInfo",
            String::class.java,
            Int::class.java,
            object : XC_MethodHook() {
                override fun beforeHookedMethod(param: MethodHookParam?) {
                    super.beforeHookedMethod(param)
                    val packageName = param?.args?.get(0) as String

                    if (packageName == PACKAGE_NAME) return

                    val fakeSignature = Signature(DC_SIGNATURE)
                    val fakePackageInfo = PackageInfo()
                    fakePackageInfo.signatures = arrayOf(fakeSignature)
                    param.result = fakePackageInfo
                }
            })
    }

    override fun handleInitPackageResources(resparam: InitPackageResourcesParam) {
        if (resparam.packageName != PACKAGE_NAME) return

        val adDimens = listOf(
            "ad_main_small_native",
            "ad_minimum",
            "ad_minimum_tall",
            "main_ad_live_best_spacing",
            "read_ad_minimum",
            "image_ad"
        )

        val zero = XResources.DimensionReplacement(0f, TypedValue.COMPLEX_UNIT_DIP)

        for (dimenName in adDimens) {
            resparam.res.setReplacement(
                resparam.packageName, "dimen", dimenName, zero
            )
        }
    }
}