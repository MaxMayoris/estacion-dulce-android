package com.estaciondulce.app.fragments

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment
import com.estaciondulce.app.R

/**
 * A simple [Fragment] subclass.
 * Use the [TransactionFragment.newInstance] factory method to
 * create an instance of this fragment.
 */
class TransactionFragment : Fragment() {
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val tvVersion = view.findViewById<TextView>(R.id.appVersionText)

        val versionName = requireActivity().packageManager
            .getPackageInfo(requireActivity().packageName, 0)
            .versionName

        tvVersion.text = "Versión: $versionName"
    }
}