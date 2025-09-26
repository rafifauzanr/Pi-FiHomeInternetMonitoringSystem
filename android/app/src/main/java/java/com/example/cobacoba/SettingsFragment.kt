package com.example.cobacoba

import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import android.content.Context

class SettingsFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        (activity as? AppCompatActivity)?.supportActionBar?.title = "Settings"
        val view = inflater.inflate(R.layout.fragment_settings, container, false)

        val aboutSetting = view.findViewById<LinearLayout>(R.id.wiki_indicator)
        aboutSetting.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, WikiIndicatorFragment())
                .addToBackStack(null)
                .commit()
        }

        val renameSetting = view.findViewById<LinearLayout>(R.id.rename_device_setting)
        renameSetting.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, RenameDeviceFragment())
                .addToBackStack(null)
                .commit()
        }

        val changePinSetting = view.findViewById<LinearLayout>(R.id.change_pin_setting)
        changePinSetting.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragment_container, ChangePinFragment())
                .addToBackStack(null)
                .commit()
        }

        return view
    }

    override fun onDestroyView() {
        super.onDestroyView()
        (activity as? AppCompatActivity)?.supportActionBar?.title = getString(R.string.app_name)
    }
}