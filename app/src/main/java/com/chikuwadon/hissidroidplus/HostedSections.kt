package com.chikuwadon.hissidroidplus

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider

/** Hosted destinations do not own a Window or a bottom navigation bar. */
class ArchiveFragment : Fragment() {
    private var content: ArchiveContent? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val host = requireActivity()
        val root = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(host.getColor(R.color.surface))
        }
        val screen = ArchiveContent(host, ViewModelProvider(this)[ArchiveScreenState::class.java])
        content = screen
        screen.buildScreen(root)
        return root
    }

    override fun onResume() {
        super.onResume()
        content?.resume()
    }

    override fun onDestroyView() {
        content?.dispose()
        content = null
        super.onDestroyView()
    }
}

class SettingsFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val host = requireActivity()
        val root = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(host.getColor(R.color.surface))
        }
        val content = SettingsContent(host)
        content.populate(content.build(root, null))
        return root
    }
}

/** The same content is used by legacy activities and the shared navigation host. */
open class HistoryFragment : Fragment() {
    protected open val favoritesScreen = false
    private var content: HistoryContent? = null

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val screen = HistoryContent(requireActivity(), ViewModelProvider(this)[HistoryScreenState::class.java], favoritesScreen)
        content = screen
        return screen.build(state)
    }

    override fun onResume() {
        super.onResume()
        content?.resume()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        content?.save(outState)
        super.onSaveInstanceState(outState)
    }

    fun handleBack(): Boolean = content?.handleBack() ?: false

    override fun onDestroyView() {
        content?.dispose()
        content = null
        super.onDestroyView()
    }
}

class FavoritesFragment : HistoryFragment() {
    override val favoritesScreen = true
}
