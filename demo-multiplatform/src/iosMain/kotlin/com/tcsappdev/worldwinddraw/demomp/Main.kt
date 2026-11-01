@file:OptIn(ExperimentalForeignApi::class, BetaInteropApi::class)

package com.tcsappdev.worldwinddraw.demomp

import androidx.compose.ui.window.ComposeUIViewController
import kotlinx.cinterop.BetaInteropApi
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.ObjCObjectBase.OverrideInit
import kotlinx.cinterop.cstr
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.toCValues
import kotlinx.cinterop.ExportObjCClass
import platform.Foundation.NSStringFromClass
import platform.UIKit.UIApplication
import platform.UIKit.UIApplicationDelegateProtocol
import platform.UIKit.UIApplicationDelegateProtocolMeta
import platform.UIKit.UIApplicationMain
import platform.UIKit.UIResponder
import platform.UIKit.UIResponderMeta
import platform.UIKit.UIScreen
import platform.UIKit.UIWindow

// A whole iOS app in Kotlin, so the check needs no Xcode project: the .app is put together by scripts/ios-sim-run.sh.
fun main() {
    val args = arrayOf("demo-multiplatform")
    memScoped {
        val argv = args.map { it.cstr.ptr }.toCValues()
        UIApplicationMain(args.size, argv, null, NSStringFromClass(AppDelegate))
    }
}

@ExportObjCClass
class AppDelegate : UIResponder, UIApplicationDelegateProtocol {
    companion object : UIResponderMeta(), UIApplicationDelegateProtocolMeta

    @OverrideInit
    constructor() : super()

    private var appWindow: UIWindow? = null

    override fun window(): UIWindow? = appWindow

    override fun setWindow(window: UIWindow?) {
        appWindow = window
    }

    override fun application(application: UIApplication, didFinishLaunchingWithOptions: Map<Any?, *>?): Boolean {
        val window = UIWindow(frame = UIScreen.mainScreen.bounds)
        window.rootViewController = ComposeUIViewController { App() }
        window.makeKeyAndVisible()
        appWindow = window
        return true
    }
}
