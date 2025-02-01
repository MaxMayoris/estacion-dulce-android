package com.estaciondulce.app

import android.os.Bundle
import android.view.View
import android.widget.TextView
import androidx.fragment.app.Fragment

class HomeFragment : Fragment(R.layout.fragment_home) {

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Busca el TextView dentro de la vista inflada del fragmento
        val tvVersion = view.findViewById<TextView>(R.id.appVersionText)

        // Obtén la versión de la app
        val versionName = requireActivity().packageManager
            .getPackageInfo(requireActivity().packageName, 0)
            .versionName

        // Asigna la versión al TextView
        tvVersion.text = "Versión: $versionName"
    }
}
