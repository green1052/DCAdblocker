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
import java.util.Vector
import java.util.zip.ZipFile

private const val PACKAGE_NAME = "com.dcinside.app.android"

class MainHook : IXposedHookLoadPackage, IXposedHookZygoteInit {
    companion object {
        var modulePath: String? = null
        var banMap: Map<String, Vector<String>> = emptyMap()
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
                            val type = object : TypeToken<Map<String, Vector<String>>>() {}.type
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
            className = "com.dcinside.app.model.W",
            targetMethod = "a1",
            userIdMethod = "n",
            classLoader = lpparam.classLoader
        )

        hookUserIp(
            className = "com.dcinside.app.model.W",
            targetMethod = "S0",
            classLoader = lpparam.classLoader
        )

        hookUserId(
            className = "com.dcinside.app.response.j",
            targetMethod = "Y",
            userIdMethod = "g0",
            classLoader = lpparam.classLoader
        )

        hookUserIp(
            className = "com.dcinside.app.response.j",
            targetMethod = "R",
            classLoader = lpparam.classLoader
        )

        hookUserId(
            className = "com.dcinside.app.response.PostItem",
            targetMethod = "w",
            userIdMethod = "M",
            classLoader = lpparam.classLoader
        )

        hookUserIp(
            className = "com.dcinside.app.response.PostItem",
            targetMethod = "q",
            classLoader = lpparam.classLoader
        )

        try {
            XposedHelpers.findAndHookMethod(
                "com.dcinside.app.model.W",
                lpparam.classLoader,
                "Z0",
                object : XC_MethodHook() {
                    override fun afterHookedMethod(param: MethodHookParam) {
                        val original = param.result as? String ?: return

                        XposedBridge.log(original)

                        val imgPattern = """<img\b[^>]*>""".toRegex()
                        val imgSrcPattern = """\bsrc="([^"]+)"""".toRegex()
                        val mp4Pattern = """\bdata-mp4="([^"]+)"""".toRegex()
                        val gifClassPattern = """\bclass="[^"]*\bgif\b[^"]*"""".toRegex()

                        val gifFixedResult = imgPattern.replace(original) { matchResult ->
                            val tag = matchResult.value

                            val posterValue = imgSrcPattern.find(tag)?.groupValues?.get(1)
                            val videoValue = mp4Pattern.find(tag)?.groupValues?.get(1)
                            val isGif = gifClassPattern.containsMatchIn(tag)

                            if (posterValue != null && videoValue != null && isGif) {
                                """<video controls playsinline autoplay muted loop poster="$posterValue" style="width:100%;height:300px;"><source src="$videoValue" type="video/mp4"></video>"""
                            } else {
                                tag.replaceFirst("""<img\b""".toRegex(), """<img loading="eager"""")
                            }
                        }

                        val iframePattern = """<iframe\b[^>]*>""".toRegex()
                        val iframeSrcPattern = """\bsrc="([^"]+)"""".toRegex()

                        val finalResult = iframePattern.replace(gifFixedResult) { matchResult ->
                            val iframeTag = matchResult.value
                            val srcValue = iframeSrcPattern.find(iframeTag)?.groupValues?.get(1)

                            if (
                                srcValue != null &&
                                srcValue.startsWith("https://app.dcinside.com/movie/player") &&
                                srcValue.contains("is_copy=0")
                            ) {
                                iframeTag.replace("is_copy=0", "is_copy=1")
                            } else {
                                iframeTag
                            }
                        }

                        XposedBridge.log(finalResult)

                        param.result = finalResult
                    }
                }
            )
        } catch (t: Throwable) {
            XposedBridge.log(t)
        }

//        XposedHelpers.findAndHookMethod(
//            "com.dcinside.app.view.F",
//            lpparam.classLoader,
//            "k0",
//            "androidx.lifecycle.LifecycleOwner",
//            $$"com.dcinside.app.read.C$a",
//            object : XC_MethodReplacement() {
//                override fun replaceHookedMethod(param: MethodHookParam): Any? {
//                    return null
//                }
//            }
//        )

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
