package com.limelight

import com.limelight.preferences.PreferenceConfiguration
import com.limelight.ui.FloatBallManager

/**
 * 悬浮球初始化和手势动作分发。
 * 从 Game.java 提取，管理 FloatBallManager 的生命周期和交互回调。
 */
class FloatBallHandler(private val game: Game, private val prefConfig: PreferenceConfiguration) {

    var manager: FloatBallManager? = null
        private set
    private val actionExecutor = StreamActionExecutor(game, { game.conn })
    private var visible = false

    /**
     * 根据设置决定是否创建悬浮球并注册交互监听器。
     * 应在 onCreate 末尾调用。
     */
    fun initialize() {
        if (!prefConfig.enableFloatBall) return

        // 大小固定为50dp，透明度固定为100%
        // 根据自动隐藏延迟判断是否启用边缘吸附：延迟为0时不启用，此时用户可自由放置
        val enableEdgeSnap = prefConfig.floatBallAutoHideDelay > 0
        val mgr = FloatBallManager(
            game,
            50,   // 固定大小50dp
            100,  // 固定透明度100%
            prefConfig.floatBallAutoHideDelay.toLong(),
            enableEdgeSnap
        )

        mgr.setOnFloatBallInteractListener(object : FloatBallManager.OnFloatBallInteractListener {
            override fun onSingleClick() {
                executeAction(prefConfig.floatBallSingleClickAction)
                LimeLog.info("FloatBall: 单击被触发，执行动作: ${prefConfig.floatBallSingleClickAction}")
            }

            override fun onDoubleClick() {
                executeAction(prefConfig.floatBallDoubleClickAction)
                LimeLog.info("FloatBall: 双击被触发，执行动作: ${prefConfig.floatBallDoubleClickAction}")
            }

            override fun onLongClick() {
                executeAction(prefConfig.floatBallLongClickAction)
                LimeLog.info("FloatBall: 长按被触发，执行动作: ${prefConfig.floatBallLongClickAction}")
            }

            override fun onSwipe(direction: FloatBallManager.SwipeDirection) {
                val actionToExecute = when (direction) {
                    FloatBallManager.SwipeDirection.UP -> {
                        LimeLog.info("FloatBall: 向上滑动，执行动作: ${prefConfig.floatBallSwipeUpAction}")
                        prefConfig.floatBallSwipeUpAction
                    }
                    FloatBallManager.SwipeDirection.DOWN -> {
                        LimeLog.info("FloatBall: 向下滑动，执行动作: ${prefConfig.floatBallSwipeDownAction}")
                        prefConfig.floatBallSwipeDownAction
                    }
                    FloatBallManager.SwipeDirection.LEFT -> {
                        LimeLog.info("FloatBall: 向左滑动，执行动作: ${prefConfig.floatBallSwipeLeftAction}")
                        prefConfig.floatBallSwipeLeftAction
                    }
                    FloatBallManager.SwipeDirection.RIGHT -> {
                        LimeLog.info("FloatBall: 向右滑动，执行动作: ${prefConfig.floatBallSwipeRightAction}")
                        prefConfig.floatBallSwipeRightAction
                    }
                }
                if (actionToExecute != "none") {
                    executeAction(actionToExecute)
                }
            }
        })

        manager = mgr
    }

    fun show() = manager?.showFloatBall()
        ?.also { visible = true }

    fun hide() = manager?.hideFloatBall()
        ?.also { visible = false }

    fun toggleVisibility() {
        if (visible) hide() else show()
    }

    fun release() {
        manager?.release()
        manager = null
    }

    private fun executeAction(actionType: String?) {
        if (actionType.isNullOrEmpty() || actionType == "none") return

        if (!actionExecutor.execute(actionType)) {
            LimeLog.warning("Unknown float ball action: $actionType")
        }
    }
}
