package com.green1052.dcadblocker

import android.net.Uri
import android.webkit.WebResourceResponse
import android.webkit.WebView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import de.robv.android.xposed.IXposedHookLoadPackage
import de.robv.android.xposed.IXposedHookZygoteInit
import de.robv.android.xposed.XC_MethodHook
import de.robv.android.xposed.XC_MethodReplacement
import de.robv.android.xposed.XposedBridge
import de.robv.android.xposed.XposedHelpers
import de.robv.android.xposed.callbacks.XC_LoadPackage
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.util.zip.ZipFile

private const val PACKAGE_NAME = "com.dcinside.app.android"

class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {
    companion object {
        var modulePath: String? = null
        var banMap: Map<String, List<String>> = emptyMap()
        var ipMap: Map<String, String> = emptyMap()
    }

    override fun initZygote(startupParam: IXposedHookZygoteInit.StartupParam) {
        modulePath = startupParam.modulePath
        loadAssets()
    }

    private fun loadAssets() {
        try {
            modulePath?.let { path ->
                ZipFile(path).use { zip ->
                    val banEntry = zip.getEntry("assets/ban.json")
                    if (banEntry != null) {
                        zip.getInputStream(banEntry).use { stream ->
                            val type = object : TypeToken<Map<String, List<String>>>() {}.type
                            banMap = Gson().fromJson(InputStreamReader(stream), type)
                        }
                    }

                    val ipEntry = zip.getEntry("assets/ip.json")
                    if (ipEntry != null) {
                        zip.getInputStream(ipEntry).use { stream ->
                            val type = object : TypeToken<Map<String, String>>() {}.type
                            ipMap = Gson().fromJson(InputStreamReader(stream), type)
                        }
                    }
                }
            }
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

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

                        if (result.contains(userId)) return

                        val info =
                            banMap.filter { it.value.contains(userId) }.keys.joinToString(", ")
                        val banInfo = if (info.isNotEmpty()) " [$info]" else ""

                        param.result = "$result($userId)$banInfo"
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }
    }

    private fun hookUserIp(
        className: String,
        targetMethod: String,
        classLoader: ClassLoader
    ) {
        try {
            XposedHelpers.findAndHookMethod(
                className,
                classLoader,
                targetMethod,
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val result = param.result.toString()

                        if (result.isEmpty()) return

                        val info = ipMap[result]
                        val ipInfo = if (info != null) " [$info]" else ""

                        param.result = "$result$ipInfo"
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

        hookUserIp(
            className = "com.dcinside.app.model.Q",
            targetMethod = "R0",
            classLoader = lpparam.classLoader
        )

        hookUserId(
            className = "com.dcinside.app.response.j",
            targetMethod = "X",
            userIdMethod = "f0",
            classLoader = lpparam.classLoader
        )

        hookUserIp(
            className = "com.dcinside.app.response.j",
            targetMethod = "R",
            classLoader = lpparam.classLoader
        )

        hookUserId(
            className = "com.dcinside.app.response.PostItem",
            targetMethod = "z",
            userIdMethod = "N",
            classLoader = lpparam.classLoader
        )

        hookUserIp(
            className = "com.dcinside.app.response.PostItem",
            targetMethod = "u",
            classLoader = lpparam.classLoader
        )

        try {
            XposedHelpers.findAndHookMethod(
                "com.dcinside.app.model.S.a",
                lpparam.classLoader,
                "j",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val original = param.result as String

                        val pattern =
                            """&lt;img\s+src=&quot;(.+?)&quot;.*?class=&quot;gif&quot;.*?data-mp4=&quot;(.+?)&quot;.*?&gt;""".toRegex()
                        val result = pattern.replace(original) { matchResult ->
                            val posterValue = matchResult.groupValues[1]
                            val videoValue = matchResult.groupValues[2]
                            """&lt;video controls playsinline autoplay muted loop poster=&quot;$posterValue&quot; style=&quot;width:100%;max-width:100%;&quot;&gt;&lt;source src=&quot;$videoValue&quot; type=&quot;video/mp4&quot;&gt;&lt;/video&gt;"""
                        }

                        val imgPattern = """&lt;img\s+""".toRegex()
                        val finalResult = imgPattern.replace(result) {
                            """&lt;img loading=&quot;lazy&quot; """
                        }

                        param.result = finalResult
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }

        XposedHelpers.findAndHookMethod(
            "com.dcinside.app.view.F",
            lpparam.classLoader,
            "k0",
            "androidx.lifecycle.LifecycleOwner",
            $$"com.dcinside.app.read.C$a",
            object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam): Any? {
                    return null
                }
            }
        )

        XposedHelpers.findAndHookMethod(
            "com.kakao.adfit.ads.ba.BannerAdView",
            lpparam.classLoader,
            "loadAd",
            object : XC_MethodReplacement() {
                override fun replaceHookedMethod(param: MethodHookParam): Any? {
                    return null
                }
            }
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
            }
        )
    }
}