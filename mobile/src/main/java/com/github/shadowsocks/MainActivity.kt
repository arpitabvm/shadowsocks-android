/*******************************************************************************
 *                                                                             *
 *  Copyright (C) 2017 by Max Lv <max.c.lv@gmail.com>                          *
 *  Copyright (C) 2017 by Mygod Studio <contact-shadowsocks-android@mygod.be>  *
 *                                                                             *
 *  This program is free software: you can redistribute it and/or modify       *
 *  it under the terms of the GNU General Public License as published by       *
 *  the Free Software Foundation, either version 3 of the License, or          *
 *  (at your option) any later version.                                        *
 *                                                                             *
 *  This program is distributed in the hope that it will be useful,            *
 *  but WITHOUT ANY WARRANTY; without even the implied warranty of             *
 *  MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the              *
 *  GNU General Public License for more details.                               *
 *                                                                             *
 *  You should have received a copy of the GNU General Public License          *
 *  along with this program. If not, see <http://www.gnu.org/licenses/>.       *
 *                                                                             *
 *******************************************************************************/

package com.github.shadowsocks

import android.content.ActivityNotFoundException
import android.os.Bundle
import android.os.RemoteException
import android.view.KeyCharacterMap
import android.view.KeyEvent
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.browser.customtabs.CustomTabColorSchemeParams
import androidx.browser.customtabs.CustomTabsIntent
import androidx.coordinatorlayout.widget.CoordinatorLayout
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.core.view.*
import androidx.drawerlayout.widget.DrawerLayout
import androidx.preference.PreferenceDataStore
import com.github.shadowsocks.aidl.IShadowsocksService
import com.github.shadowsocks.aidl.ShadowsocksConnection
import com.github.shadowsocks.aidl.TrafficStats
import com.github.shadowsocks.bg.BaseService
import com.github.shadowsocks.database.Profile
import com.github.shadowsocks.database.ProfileManager
import com.github.shadowsocks.preference.DataStore
import com.github.shadowsocks.preference.OnPreferenceDataStoreChangeListener
import com.github.shadowsocks.utils.Key
import com.github.shadowsocks.utils.StartService
import com.github.shadowsocks.widget.ListHolderListener
import com.github.shadowsocks.widget.ServiceButton
import com.github.shadowsocks.widget.StatsBar
import com.google.android.material.snackbar.Snackbar

class MainActivity : AppCompatActivity(), ShadowsocksConnection.Callback,
    OnPreferenceDataStoreChangeListener {
    companion object {
        var stateListener: ((BaseService.State) -> Unit)? = null
    }

    // UI
    private lateinit var fab: ServiceButton
    private lateinit var stats: StatsBar

    lateinit var snackbar: CoordinatorLayout private set
    fun snackbar(text: CharSequence = "") =
        Snackbar.make(snackbar, text, Snackbar.LENGTH_LONG).apply {
            anchorView = fab
        }

    private val customTabsIntent by lazy {
        CustomTabsIntent.Builder().apply {
            setColorScheme(CustomTabsIntent.COLOR_SCHEME_SYSTEM)
            setColorSchemeParams(
                CustomTabsIntent.COLOR_SCHEME_LIGHT, CustomTabColorSchemeParams.Builder().apply {
                    setToolbarColor(
                        ContextCompat.getColor(
                            this@MainActivity, R.color.light_color_primary
                        )
                    )
                }.build()
            )
            setColorSchemeParams(
                CustomTabsIntent.COLOR_SCHEME_DARK, CustomTabColorSchemeParams.Builder().apply {
                    setToolbarColor(
                        ContextCompat.getColor(
                            this@MainActivity, R.color.dark_color_primary
                        )
                    )
                }.build()
            )
        }.build()
    }

    fun launchUrl(uri: String) = try {
        customTabsIntent.launchUrl(this, uri.toUri())
    } catch (_: ActivityNotFoundException) {
        snackbar(uri).show()
    }

    // service
    var state = BaseService.State.Idle
    override fun stateChanged(state: BaseService.State, profileName: String?, msg: String?) =
        changeState(state, msg)

    override fun trafficUpdated(profileId: Long, stats: TrafficStats) {
        if (profileId == 0L) this@MainActivity.stats.updateTraffic(
            stats.txRate, stats.rxRate, stats.txTotal, stats.rxTotal
        )
        if (state != BaseService.State.Stopping) {
//            (supportFragmentManager.findFragmentById(R.id.fragment_holder) as? ProfilesFragment)?.onTrafficUpdated(
//                profileId,
//                stats
//            )
        }
    }

    override fun trafficPersisted(profileId: Long) {
//        ProfilesFragment.instance?.onTrafficPersisted(profileId)
    }

    private fun changeState(
        state: BaseService.State, msg: String? = null, animate: Boolean = true
    ) {
        fab.changeState(state, this.state, animate)
        stats.changeState(state, animate)
        if (msg != null) snackbar(getString(R.string.vpn_error, msg)).show()
        this.state = state
//        ProfilesFragment.instance?.profilesAdapter?.notifyDataSetChanged()  // refresh button enabled state
        stateListener?.invoke(state)
    }

    private fun toggle() = if (state.canStop) Core.stopService() else connect.launch(null)

    private val connection = ShadowsocksConnection(true)
    override fun onServiceConnected(service: IShadowsocksService) = changeState(
        try {
            BaseService.State.values()[service.state]
        } catch (_: RemoteException) {
            BaseService.State.Idle
        }
    )

    override fun onServiceDisconnected() = changeState(BaseService.State.Idle)
    override fun onBinderDied() {
        connection.disconnect(this)
        connection.connect(this, this)
    }

    private val connect = registerForActivityResult(StartService()) {
        if (it) snackbar().setText(R.string.vpn_permission_denied).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        setContentView(R.layout.layout_main)
        snackbar = findViewById(R.id.snackbar)
        ViewCompat.setOnApplyWindowInsetsListener(snackbar, ListHolderListener)
        stats = findViewById(R.id.stats)
        stats.setOnClickListener { if (state == BaseService.State.Connected) stats.testConnection() }



        Profile().host = "au2.ssocks.xyz"
        Profile().remotePort = 2087
        Profile().password = "WTh5V7Jq63AkdPZu"
        Profile().method = "chacha20-ietf-poly1305"

        ProfileManager.ensureNotEmpty()
//        if (savedInstanceState == null) {
//            displayFragment(ProfilesFragment())
//        }

        fab = findViewById(R.id.fab)
        fab.initProgress(findViewById(R.id.fabProgress))
        fab.setOnClickListener { toggle() }
        ViewCompat.setOnApplyWindowInsetsListener(fab) { view, insets ->
            view.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin =
                    insets.getInsets(WindowInsetsCompat.Type.navigationBars()).bottom + resources.getDimensionPixelOffset(
                        R.dimen.mtrl_bottomappbar_fab_bottom_margin
                    )
            }
            insets
        }

        changeState(BaseService.State.Idle, animate = false)    // reset everything to init state
        connection.connect(this, this)
        DataStore.publicStore.registerChangeListener(this)
    }

    override fun onPreferenceDataStoreChanged(store: PreferenceDataStore, key: String) {
        when (key) {
            Key.serviceMode -> {
                connection.disconnect(this)
                connection.connect(this, this)
            }
        }
    }


    override fun onStart() {
        super.onStart()
        connection.bandwidthTimeout = 500
    }

    override fun onKeyShortcut(keyCode: Int, event: KeyEvent) = when {
        keyCode == KeyEvent.KEYCODE_G && event.hasModifiers(KeyEvent.META_CTRL_ON) -> {
            toggle()
            true
        }

        keyCode == KeyEvent.KEYCODE_T && event.hasModifiers(KeyEvent.META_CTRL_ON) -> {
            stats.testConnection()
            true
        }

        else -> true
    }

    override fun onStop() {
        connection.bandwidthTimeout = 0
        super.onStop()
    }

    override fun onDestroy() {
        super.onDestroy()
        DataStore.publicStore.unregisterChangeListener(this)
        connection.disconnect(this)
    }
}
