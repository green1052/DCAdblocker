package com.green1052.dcadblocker

import android.net.Uri
import android.util.Log
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import io.github.libxposed.api.XposedModule
import io.github.libxposed.api.XposedModuleInterface
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.lang.reflect.Method
import java.util.zip.ZipFile

private const val PACKAGE_NAME = "com.dcinside.app.android"
private const val TAG = "DCAdblocker"

class MainHook : XposedModule() {
    companion object {
        var banMap: Map<String, List<String>> = emptyMap()
        var ipMap: Map<String, String> = emptyMap()
    }

    override fun onPackageLoaded(param: XposedModuleInterface.PackageLoadedParam) {
        if (param.packageName != PACKAGE_NAME) return

        loadAssets()

        val classLoader = param.defaultClassLoader

        hookUserId(
            className = "com.dcinside.app.model.W",
            targetMethod = "a1",
            userIdMethod = "n",
            classLoader = classLoader
        )

        hookUserIp(
            className = "com.dcinside.app.model.W",
            targetMethod = "S0",
            classLoader = classLoader
        )

        hookUserId(
            className = "com.dcinside.app.response.j",
            targetMethod = "Y",
            userIdMethod = "g0",
            classLoader = classLoader
        )

        hookUserIp(
            className = "com.dcinside.app.response.j",
            targetMethod = "R",
            classLoader = classLoader
        )

        hookUserId(
            className = "com.dcinside.app.response.PostItem",
            targetMethod = "w",
            userIdMethod = "M",
            classLoader = classLoader
        )

        hookUserIp(
            className = "com.dcinside.app.response.PostItem",
            targetMethod = "q",
            classLoader = classLoader
        )

        hookContentHtml(classLoader)
        hookBannerAd(classLoader)
        hookNaverAdRequest(classLoader)
    }

    private fun loadAssets() {
        try {
            ZipFile(moduleApplicationInfo.sourceDir).use { zip ->
                zip.getEntry("assets/ban.json")?.let { banEntry ->
                    zip.getInputStream(banEntry).use { stream ->
                        val type = object : TypeToken<Map<String, List<String>>>() {}.type
                        banMap = Gson().fromJson(InputStreamReader(stream), type)
                    }
                }

                zip.getEntry("assets/ip.json")?.let { ipEntry ->
                    zip.getInputStream(ipEntry).use { stream ->
                        val type = object : TypeToken<Map<String, String>>() {}.type
                        ipMap = Gson().fromJson(InputStreamReader(stream), type)
                    }
                }
            }
        } catch (t: Throwable) {
            log(t)
        }
    }

    private fun hookUserId(
        className: String,
        targetMethod: String,
        userIdMethod: String,
        classLoader: ClassLoader
    ) {
        try {
            val method = findMethod(className, targetMethod, classLoader)
            hook(method).intercept { chain ->
                val result = chain.proceed()?.toString() ?: return@intercept null
                val userId = callMethod(chain.thisObject, userIdMethod) as? String
                    ?: return@intercept result

                if (result.contains(userId)) return@intercept result

                val info = banMap.filter { it.value.contains(userId) }.keys.joinToString(", ")
                val banInfo = if (info.isNotEmpty()) " [$info]" else ""

                "$result($userId)$banInfo"
            }
        } catch (t: Throwable) {
            log(t)
        }
    }

    private fun hookUserIp(
        className: String,
        targetMethod: String,
        classLoader: ClassLoader
    ) {
        try {
            val method = findMethod(className, targetMethod, classLoader)
            hook(method).intercept { chain ->
                val result = chain.proceed()?.toString() ?: return@intercept null

                if (result.isEmpty()) return@intercept result

                val info = ipMap[result]
                val ipInfo = if (info != null) " [$info]" else ""

                "$result$ipInfo"
            }
        } catch (t: Throwable) {
            log(t)
        }
    }

    private fun hookContentHtml(classLoader: ClassLoader) {
        try {
            val method = findMethod("com.dcinside.app.model.W", "Z0", classLoader)
            hook(method).intercept { chain ->
                val original = chain.proceed() as? String ?: return@intercept null

                log(original)

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

                log(finalResult)

                finalResult
            }
        } catch (t: Throwable) {
            log(t)
        }
    }

    private fun hookBannerAd(classLoader: ClassLoader) {
        try {
            val method = findMethod("com.kakao.adfit.ads.ba.BannerAdView", "loadAd", classLoader)
            hook(method).intercept { null }
        } catch (t: Throwable) {
            log(t)
        }
    }

    private fun hookNaverAdRequest(classLoader: ClassLoader) {
        try {
            val method = findMethod(
                "android.webkit.WebViewClient",
                "shouldInterceptRequest",
                classLoader,
                WebView::class.java,
                WebResourceRequest::class.java
            )

            hook(method).intercept { chain ->
                val uri = (chain.getArg(1) as WebResourceRequest).url as Uri

                if (!uri.toString().contains("app.dcinside.com/api/_naverad")) {
                    return@intercept chain.proceed()
                }

                WebResourceResponse(
                    "text/plain",
                    "UTF-8",
                    ByteArrayInputStream("".toByteArray())
                )
            }
        } catch (t: Throwable) {
            log(t)
        }
    }

    private fun findMethod(
        className: String,
        methodName: String,
        classLoader: ClassLoader,
        vararg parameterTypes: Class<*>
    ): Method {
        return Class.forName(className, false, classLoader)
            .getDeclaredMethod(methodName, *parameterTypes)
            .also { it.isAccessible = true }
    }

    private fun callMethod(instance: Any?, methodName: String): Any? {
        if (instance == null) return null

        return instance.javaClass
            .getDeclaredMethod(methodName)
            .also { it.isAccessible = true }
            .invoke(instance)
    }

    private fun log(message: String) {
        log(Log.DEBUG, TAG, message)
    }

    private fun log(t: Throwable) {
        log(Log.ERROR, TAG, t.message ?: t.javaClass.name, t)
    }
}
