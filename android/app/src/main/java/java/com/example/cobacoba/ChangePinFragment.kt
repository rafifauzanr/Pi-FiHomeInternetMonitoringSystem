package com.example.cobacoba

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.Toast
import androidx.fragment.app.Fragment

class ChangePinFragment : Fragment() {

    private lateinit var oldPinEditText: EditText
    private lateinit var newPinEditText: EditText
    private lateinit var confirmNewPinEditText: EditText
    private lateinit var savePinButton: Button

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.fragment_change_pin, container, false)

        oldPinEditText = view.findViewById(R.id.oldPinEditText)
        newPinEditText = view.findViewById(R.id.newPinEditText)
        confirmNewPinEditText = view.findViewById(R.id.confirmNewPinEditText)
        savePinButton = view.findViewById(R.id.savePinButton)

        savePinButton.setOnClickListener {
            changePin()
        }

        return view
    }

    private fun changePin() {
        val oldPin = oldPinEditText.text.toString()
        val newPin = newPinEditText.text.toString()
        val confirmNewPin = confirmNewPinEditText.text.toString()

        val prefs = requireActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val storedPin = prefs.getString("pin", "1234") ?: "1234"

        if (oldPin != storedPin) {
            Toast.makeText(requireContext(), "PIN lama salah!", Toast.LENGTH_SHORT).show()
            return
        }

        if (newPin.length != 4) {
            Toast.makeText(requireContext(), "PIN baru harus 4 digit!", Toast.LENGTH_SHORT).show()
            return
        }

        if (newPin != confirmNewPin) {
            Toast.makeText(requireContext(), "Konfirmasi PIN baru tidak cocok!", Toast.LENGTH_SHORT).show()
            return
        }

        prefs.edit().putString("pin", newPin).apply()
        Toast.makeText(requireContext(), "PIN berhasil diubah!", Toast.LENGTH_SHORT).show()
        parentFragmentManager.popBackStack()
    }
}

