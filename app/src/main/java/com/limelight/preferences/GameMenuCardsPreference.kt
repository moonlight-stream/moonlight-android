package com.limelight.preferences

import android.content.Context
import android.util.AttributeSet
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.limelight.R
import com.limelight.gamemenu.GameMenuCardVisibilityEditor

/** Configures the same game-menu card visibility flags used by the in-stream editor. */
class GameMenuCardsPreference : Preference {

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int, defStyleRes: Int) :
            super(context, attrs, defStyleAttr, defStyleRes) { initialize() }

    constructor(context: Context, attrs: AttributeSet?, defStyleAttr: Int) :
            super(context, attrs, defStyleAttr) { initialize() }

    constructor(context: Context, attrs: AttributeSet?) : super(context, attrs) { initialize() }

    constructor(context: Context) : super(context) { initialize() }

    private fun initialize() {
        isPersistent = false
    }

    override fun onAttachedToHierarchy(preferenceManager: PreferenceManager) {
        super.onAttachedToHierarchy(preferenceManager)
        updateSummary(PreferenceConfiguration.readPreferences(context))
    }

    override fun onClick() {
        val config = PreferenceConfiguration.readPreferences(context)
        GameMenuCardVisibilityEditor.show(context, config, ::updateSummary)
    }

    private fun updateSummary(config: PreferenceConfiguration) {
        val selectedNames = GameMenuCardVisibilityEditor.selectedLabels(context, config)
        summary = if (selectedNames.isEmpty()) {
            context.getString(R.string.summary_game_menu_cards_none)
        } else {
            context.getString(
                R.string.summary_game_menu_cards_selected,
                selectedNames.joinToString(context.getString(R.string.game_menu_card_list_separator))
            )
        }
    }
}
