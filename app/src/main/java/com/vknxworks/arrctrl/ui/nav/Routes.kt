package com.vknxworks.arrctrl.ui.nav

import kotlinx.serialization.Serializable

// Tabs
@Serializable data object MoviesRoute
@Serializable data object SeriesRoute
@Serializable data object CalendarRoute
@Serializable data object ActivityRoute
@Serializable data object MoreRoute

// Pushed screens
@Serializable data object AccountRoute
@Serializable data object SettingsRoute

/** Add ([type] set) or edit ([id] set) a server. */
@Serializable data class ServerEditRoute(val id: String? = null, val type: String? = null)
@Serializable data object ImportRoute

// Activity
@Serializable data class TorrentRoute(val serverId: String, val hash: String)
@Serializable data class ManualImportRoute(val serverId: String, val downloadId: String, val title: String)
@Serializable data class AddTorrentRoute(val link: String? = null, val file: String? = null)

// Movies
@Serializable data class MovieRoute(val serverId: String, val movieId: Int)
@Serializable data class MovieReleasesRoute(val serverId: String, val movieId: Int, val title: String)
@Serializable data class AddMovieRoute(val serverId: String)
