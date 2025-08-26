package com.green1052.dcadblocker

import android.content.res.XResources
import android.net.Uri
import android.util.TypedValue
import android.view.View
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

    override fun handleInitPackageResources(resparam: InitPackageResourcesParam) {
        if (resparam.packageName != "com.dcinside.app.android") return

        val adDimens = listOf(
            "ad_minimum_height",
            "read_ad_minimum_height",
            "image_ad_height",
            "script_ad_size",
            "image_ad_height",
            "main_ad_live_best_spacing",
            "script_no_image_height",
            "script_no_image_width"
        )

        val zero = XResources.DimensionReplacement(0f, TypedValue.COMPLEX_UNIT_DIP)

        for (dimenName in adDimens) {
            resparam.res.setReplacement(
                resparam.packageName,
                "dimen",
                dimenName,
                zero
            )
        }
    }
}