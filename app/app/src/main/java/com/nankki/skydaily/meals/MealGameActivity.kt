package com.nankki.skydaily.meals

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import com.nankki.skydaily.MainActivity

/** 通知点击时解析游戏入口，不显示页面，也不保留在返回栈中。 */
class MealGameActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            if (savedInstanceState == null) openGame()
        } finally {
            finish()
        }
    }

    private fun openGame() {
        val target = GameLaunchPolicy.resolve { packageName ->
            packageManager.getLaunchIntentForPackage(packageName)
        }
        if (target != null) {
            try {
                startActivity(target.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                return
            } catch (_: ActivityNotFoundException) {
                // 客户端可能在解析入口后被卸载或停用。
            } catch (_: SecurityException) {
                // 系统限制启动时仍可查看饭点信息。
            }
        }
        Toast.makeText(this, "未找到可打开的国服光遇", Toast.LENGTH_SHORT).show()
        startActivity(Intent(this, MainActivity::class.java).apply {
            action = MealReminderManager.OPEN_MEALS_ACTION
            putExtra(
                MealReminderManager.EXTRA_MEAL_START,
                intent.getLongExtra(MealReminderManager.EXTRA_MEAL_START, 0L),
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        })
    }
}
