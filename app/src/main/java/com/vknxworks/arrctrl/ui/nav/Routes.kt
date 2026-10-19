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
@Serializable data object NotificationsRoute

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

// Series
@Serializable data class SeriesDetailRoute(val serverId: String, val seriesId: Int)
@Serializable data class SeasonRoute(val serverId: String, val seriesId: Int, val season: Int)
@Serializable data class SeasonReleasesRoute(val serverId: String, val seriesId: Int, val season: Int, val title: String)
@Serializable data class EpisodeReleasesRoute(val serverId: String, val episodeId: Int, val title: String)
@Serializable data class AddSeriesRoute(val serverId: String)

// More
@Serializable data object ProwlarrRoute
@Serializable data object BazarrRoute
@Serializable data object AutobrrRoute
@Serializable data object CleanuparrRoute
@Serializable data object HealthRoute
@Serializable data class MediaSubtitlesRoute(val bazarrId: String, val radarrId: Int? = null, val seriesId: Int? = null, val episodeId: Int? = null, val title: String)
@Serializable data class SubtitleSearchRoute(val serverId: String, val radarrId: Int? = null, val seriesId: Int? = null, val episodeId: Int? = null, val title: String)
