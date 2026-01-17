package com.green1052.dcadblocker

import android.content.res.XResources
import android.net.Uri
import android.util.TypedValue
import android.webkit.WebResourceResponse
import android.webkit.WebView
import de.robv.android.xposed.IXposedHookInitPackageResources
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_InitPackageResources.InitPackageResourcesParam
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.ByteArrayInputStream

private const val PACKAGE_NAME = "com.dcinside.app.android"

class MainHook : IXposedHookLoadPackage, IXposedHookInitPackageResources {
    private fun hookUserId(
        className: String,
        targetMethod: String,
        userIdMethod: String,
        classLoader: ClassLoader
    ) {
        try {
            XposedHelpers.findAndHookMethod(
                className,
                classLoader,
                targetMethod,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val userId = XposedHelpers.callMethod(
                            param.thisObject,
                            userIdMethod
                        ) as? String ?: return

                        val result = param.result.toString()

                        if (!result.contains(userId))
                            param.result = "$result($userId)"
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    override fun handleLoadPackage(lpparam: XC_LoadPackage.LoadPackageParam) {
        if (lpparam.packageName != PACKAGE_NAME) return

        hookUserId(
            className = "com.dcinside.app.model.Q",
            targetMethod = "Z0",
            userIdMethod = "n",
            classLoader = lpparam.classLoader
        )

        hookUserId(
            className = "com.dcinside.app.response.j",
            targetMethod = "X",
            userIdMethod = "f0",
            classLoader = lpparam.classLoader
        )

        hookUserId(
            className = "com.dcinside.app.response.PostItem",
            targetMethod = "z",
            userIdMethod = "N",
            classLoader = lpparam.classLoader
        )

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

        val targetClasses = listOf(
            "com.dcinside.app.view.PostReadImageAdView",
            "com.dcinside.app.ad.support.z",
            "com.kakao.adfit.AdFitSdk",
            "com.google.android.gms.ads.AdView",
            "com.kakao.adfit.ads.p335ba.BannerAdView",
            "com.igaworks.ssp.part.banner.AdPopcornSSPBannerAd",
            "com.gomfactory.adpie.sdk.AdView",
            "com.fsn.cauly.CaulyAdView",
            "com.nasmedia.admixerssp.ads.AdView"
        )

        targetClasses.forEach { className ->
            try {
                val clazz = XposedHelpers.findClass(className, lpparam.classLoader)

                clazz.declaredMethods.forEach { method ->
                    try {
                        XposedHelpers.findAndHookMethod(
                            clazz,
                            method.name,
                            object : XC_MethodReplacement() {
                                override fun replaceHookedMethod(param: MethodHookParam?): Any? {
                                    return null
                                }
                            })
                    } catch (e: Throwable) {

                    }
                }
            } catch (e: Throwable) {

            }
        }
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