package com.pitakaview.app.ui.navigation

import com.pitakaview.app.ui.components.BottomBarItem
import com.pitakaview.app.ui.icons.FitakaIcons
import com.pitakaview.app.ui.icons.icon
import com.pitakaview.app.ui.theme.Section

/** Home, then the five sections. The Filipino names are short enough for six tabs on a phone. */
val MainTabs = listOf(BottomBarItem("Home", FitakaIcons.Home)) +
    Section.entries.map { BottomBarItem(it.label, it.icon, it) }
