/*
 * Copyright (c) 2010-2023 Belledonne Communications SARL.
 *
 * This file is part of linphone-android
 * (see https://www.linphone.org).
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
package org.linphone.ui.main.settings.fragment

import android.Manifest
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.UiThread
import androidx.core.content.ContextCompat
import androidx.core.view.doOnPreDraw
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import org.linphone.R
import org.linphone.core.tools.Log
import org.linphone.databinding.AccountSettingsFragmentBinding
import org.linphone.ui.GenericActivity
import org.linphone.ui.main.fragment.GenericMainFragment
import org.linphone.utils.PasswordDialogModel
import org.linphone.ui.main.settings.viewmodel.AccountSettingsViewModel
import org.linphone.utils.DialogUtils
import org.linphone.utils.Event
import org.linphone.utils.ShortcutManualEntryDialogModel

@UiThread
class AccountSettingsFragment : GenericMainFragment() {
    companion object {
        private const val TAG = "[Account Settings Fragment]"
    }

    private lateinit var binding: AccountSettingsFragmentBinding

    private val args: AccountSettingsFragmentArgs by navArgs()

    private lateinit var viewModel: AccountSettingsViewModel

    private val requestWifiPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.i("$TAG ACCESS_FINE_LOCATION permission has been granted, WiFi SSID restriction can be used")
        } else {
            Log.w(
                "$TAG ACCESS_FINE_LOCATION permission has been denied, WiFi SSID restriction won't be able to detect the current network"
            )
        }
    }

    private val pickShortcutLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode != Activity.RESULT_OK) {
            Log.i("$TAG Shortcut picker was cancelled")
            return@registerForActivityResult
        }

        val data = result.data
        val shortcutIntent = data?.getParcelableExtra<Intent>(Intent.EXTRA_SHORTCUT_INTENT)
        if (shortcutIntent == null) {
            Log.e("$TAG Shortcut picker result didn't contain a shortcut Intent")
            return@registerForActivityResult
        }
        val name = data.getStringExtra(Intent.EXTRA_SHORTCUT_NAME).orEmpty()

        Log.i("$TAG Shortcut [$name] selected: [${shortcutIntent.toUri(Intent.URI_INTENT_SCHEME)}]")
        viewModel.setShortcut(name, shortcutIntent.toUri(Intent.URI_INTENT_SCHEME))
    }

    override fun goBack(): Boolean {
        try {
            return findNavController().popBackStack()
        } catch (ise: IllegalStateException) {
            Log.e("$TAG Can't go back popping back stack: $ise")
        }
        return false
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = AccountSettingsFragmentBinding.inflate(layoutInflater)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        postponeEnterTransition()
        super.onViewCreated(view, savedInstanceState)

        binding.lifecycleOwner = viewLifecycleOwner

        viewModel = ViewModelProvider(this)[AccountSettingsViewModel::class.java]
        binding.viewModel = viewModel
        observeToastEvents(viewModel)

        val identity = args.accountIdentity
        Log.i("$TAG Looking up for account with identity address [$identity]")
        viewModel.findAccountMatchingIdentity(identity)

        viewModel.wifiSsidAllowList.observe(viewLifecycleOwner) { ssidList ->
            if (ssidList.isNotBlank() &&
                ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.ACCESS_FINE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.i("$TAG WiFi SSID restriction is in use, requesting ACCESS_FINE_LOCATION permission")
                requestWifiPermissionLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }

        binding.setBackClickListener {
            goBack()
        }

        binding.setUpdatePasswordClickListener {
            showUpdatePasswordDialog()
        }

        binding.setOutboundProxyTooltipClickListener {
            showOutboundProxyInfoDialog()
        }

        binding.setSelectShortcutClickListener {
            launchShortcutPicker()
        }

        binding.setClearShortcutClickListener {
            viewModel.clearShortcut()
        }

        binding.setManualShortcutClickListener {
            showShortcutManualEntryDialog()
        }

        viewModel.accountFoundEvent.observe(viewLifecycleOwner) {
            it.consume { found ->
                if (found) {
                    (view.parent as? ViewGroup)?.doOnPreDraw {
                        startPostponedEnterTransition()
                    }
                } else {
                    Log.e(
                        "$TAG Failed to find an account matching this identity address [$identity]"
                    )
                    val message = getString(R.string.account_failed_to_find_identity_toast)
                    val icon = R.drawable.warning_circle
                    (requireActivity() as GenericActivity).showRedToast(message, icon)
                    goBack()
                }
            }
        }
    }

    override fun onPause() {
        super.onPause()

        viewModel.saveChanges()
        // It is possible some value have changed, causing some menu to appear or disappear
        sharedViewModel.forceUpdateAvailableNavigationItems.value = Event(true)
    }

    private fun showUpdatePasswordDialog() {
        val model = PasswordDialogModel()
        val dialog = DialogUtils.getUpdatePasswordDialog(requireContext(), model)

        model.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                dialog.dismiss()
            }
        }

        model.confirmEvent.observe(viewLifecycleOwner) {
            it.consume { password ->
                viewModel.updateAccountPassword(password)
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun showOutboundProxyInfoDialog() {
        val dialog = DialogUtils.getAccountOutboundProxyHelpDialog(requireActivity())
        dialog.show()
    }

    private fun showShortcutManualEntryDialog() {
        var label = viewModel.shortcutName.value.orEmpty()
        var action = ""
        var dataUri = ""
        var component = ""

        val currentUri = viewModel.getShortcutIntentUri()
        if (currentUri.isNotEmpty()) {
            try {
                val intent = Intent.parseUri(currentUri, Intent.URI_INTENT_SCHEME)
                action = intent.action.orEmpty()
                dataUri = intent.data?.toString().orEmpty()
                component = intent.component?.flattenToShortString().orEmpty()
            } catch (e: Exception) {
                Log.e("$TAG Failed to parse current shortcut Intent URI [$currentUri]: $e")
            }
        }

        val model = ShortcutManualEntryDialogModel(label, action, dataUri, component)
        val dialog = DialogUtils.getShortcutManualEntryDialog(requireContext(), model)

        model.dismissEvent.observe(viewLifecycleOwner) {
            it.consume {
                dialog.dismiss()
            }
        }

        model.confirmEvent.observe(viewLifecycleOwner) {
            it.consume {
                label = model.label.value.orEmpty().trim()
                action = model.action.value.orEmpty().trim()
                dataUri = model.dataUri.value.orEmpty().trim()
                component = model.component.value.orEmpty().trim()

                if (action.isEmpty()) {
                    val message = getString(R.string.account_settings_shortcut_manual_entry_invalid_toast)
                    val icon = R.drawable.warning_circle
                    (requireActivity() as GenericActivity).showRedToast(message, icon)
                    return@consume
                }

                val intent = Intent(action)
                if (dataUri.isNotEmpty()) {
                    intent.data = Uri.parse(dataUri)
                }
                if (component.isNotEmpty()) {
                    val componentName = ComponentName.unflattenFromString(component)
                    if (componentName == null) {
                        val message = getString(R.string.account_settings_shortcut_manual_entry_invalid_toast)
                        val icon = R.drawable.warning_circle
                        (requireActivity() as GenericActivity).showRedToast(message, icon)
                        return@consume
                    }
                    intent.component = componentName
                }

                val name = label.ifEmpty { action }
                Log.i("$TAG Manually configured shortcut [$name]: [${intent.toUri(Intent.URI_INTENT_SCHEME)}]")
                viewModel.setShortcut(name, intent.toUri(Intent.URI_INTENT_SCHEME))
                dialog.dismiss()
            }
        }

        dialog.show()
    }

    private fun launchShortcutPicker() {
        try {
            pickShortcutLauncher.launch(Intent(Intent.ACTION_CREATE_SHORTCUT))
        } catch (e: ActivityNotFoundException) {
            Log.e("$TAG No app on this device can provide a shortcut: $e")
            val message = getString(R.string.account_settings_shortcut_picker_not_found_toast)
            val icon = R.drawable.warning_circle
            (requireActivity() as GenericActivity).showRedToast(message, icon)
        }
    }
}
