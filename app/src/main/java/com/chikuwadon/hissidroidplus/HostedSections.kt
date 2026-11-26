package com.chikuwadon.hissidroidplus

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.fragment.app.Fragment

/** Hosted destinations do not own a Window or a bottom navigation bar. */
class ArchiveFragment : Fragment() {
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, state: Bundle?): View {
        val host = requireActivity()
        val root = LinearLayout(host).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(host.getColor(R.color.surface))
        }
        val content = ArchiveContent(host)
        content.populate(content.build(root, "過去ログ検索"))
        return root
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
        content.populate(content.build(root, "設定"))
        return root
    }
}
