package com.micewine.emu.fragments

import android.annotation.SuppressLint
import android.app.AlertDialog
import android.app.Dialog
import android.content.DialogInterface
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.EditText
import android.widget.TextView
import androidx.fragment.app.DialogFragment
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.micewine.emu.R
import com.micewine.emu.activities.MainActivity.Companion.ACTION_SETUP
import com.micewine.emu.activities.MainActivity.Companion.customRootFSPath
import com.micewine.emu.activities.MainActivity.Companion.fileManagerCwd
import com.micewine.emu.adapters.AdapterFiles
import com.micewine.emu.adapters.AdapterGame.Companion.selectedGameName
import com.micewine.emu.adapters.AdapterPreset.Companion.clickedPresetName
import com.micewine.emu.adapters.AdapterPreset.Companion.clickedPresetType
import com.micewine.emu.fragments.Box64PresetManagerFragment.Companion.exportBox64Preset
import com.micewine.emu.fragments.Box64PresetManagerFragment.Companion.importBox64Preset
import com.micewine.emu.fragments.ControllerPresetManagerFragment.Companion.exportControllerPreset
import com.micewine.emu.fragments.ControllerPresetManagerFragment.Companion.importControllerPreset
import com.micewine.emu.fragments.CreatePresetFragment.Companion.BOX64_PRESET
import com.micewine.emu.fragments.CreatePresetFragment.Companion.CONTROLLER_PRESET
import com.micewine.emu.fragments.CreatePresetFragment.Companion.VIRTUAL_CONTROLLER_PRESET
import com.micewine.emu.fragments.ShortcutsFragment.Companion.putExePath
import com.micewine.emu.fragments.VirtualControllerPresetManagerFragment.Companion.exportVirtualControllerPreset
import com.micewine.emu.fragments.VirtualControllerPresetManagerFragment.Companion.importVirtualControllerPreset
import java.io.File

class FloatingFileManagerFragment(private val operationType: Int, private val initialCwd: String = "/storage/emulated/0") : DialogFragment() {
    @SuppressLint("SetTextI18n")
    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val inflater = requireActivity().layoutInflater
        val view = inflater.inflate(R.layout.fragment_floating_file_manager, null)
        val selectRootFSFileText = view.findViewById<TextView>(R.id.selectRootFSFileText)
        val editText = view.findViewById<EditText>(R.id.editText)
        val saveButton = view.findViewById<MaterialButton>(R.id.saveButton)

        recyclerView  = view.findViewById(R.id.recyclerViewFiles)
        recyclerView?.adapter = AdapterFiles(fileList, requireContext(), true)

        fileManagerCwd = initialCwd
        fmOperationType = operationType

        refreshFiles()

        val dialog = AlertDialog.Builder(requireContext(), R.style.CustomAlertDialog).setView(view).create()

        isCancelable = operationType != OPERATION_SELECT_RAT

        when (operationType) {
            OPERATION_SELECT_RAT -> {
                selectRootFSFileText.visibility = View.VISIBLE
                editText.visibility = View.GONE
                saveButton.visibility = View.GONE

                Thread {
                    while (customRootFSPath == null) {
                        Thread.sleep(16)
                    }

                    dismiss()
                }.start()
            }
            OPERATION_EXPORT_PRESET -> {
                selectRootFSFileText.visibility = View.GONE
                editText.visibility = View.VISIBLE
                saveButton.visibility = View.VISIBLE

                when (clickedPresetType) {
                    VIRTUAL_CONTROLLER_PRESET -> editText.setText("VirtualController-$clickedPresetName.mwp")
                    CONTROLLER_PRESET -> editText.setText("PhysicalController-$clickedPresetName.mwp")
                    BOX64_PRESET -> editText.setText("Box64-$clickedPresetName.mwp")
                }

                saveButton.setOnClickListener {
                    outputFile = "$fileManagerCwd/" + editText.text.toString()

                    if (File(outputFile!!).exists()) {
                        return@setOnClickListener
                    }

                    when (clickedPresetType) {
                        VIRTUAL_CONTROLLER_PRESET -> {
                            exportVirtualControllerPreset(clickedPresetName, outputFile!!)
                            outputFile = null
                        }
                        CONTROLLER_PRESET -> {
                            exportControllerPreset(clickedPresetName, outputFile!!)
                            outputFile = null
                        }
                        BOX64_PRESET -> {
                            exportBox64Preset(requireContext(), clickedPresetName, outputFile!!)
                            outputFile = null
                        }
                    }

                    dismiss()
                }
            }
            OPERATION_IMPORT_PRESET -> {
                selectRootFSFileText.visibility = View.GONE
                editText.visibility = View.GONE
                saveButton.visibility = View.GONE

                Thread {
                    while (outputFile == null || !isAdded) {
                        Thread.sleep(16)
                    }

                    when (clickedPresetType) {
                        VIRTUAL_CONTROLLER_PRESET -> {
                            importVirtualControllerPreset(requireActivity(), outputFile!!)
                            outputFile = null
                        }
                        CONTROLLER_PRESET -> {
                            importControllerPreset(outputFile!!)
                            outputFile = null
                        }
                        BOX64_PRESET -> {
                            importBox64Preset(requireActivity(), outputFile!!)
                            outputFile = null
                        }
                    }
                    dismiss()
                }.start()
            }
            OPERATION_SELECT_EXE -> {
                selectRootFSFileText.visibility = View.GONE
                editText.visibility = View.GONE
                saveButton.visibility = View.GONE

                Thread {
                    while (outputFile == null || !isAdded) {
                        Thread.sleep(16)
                    }

                    putExePath(selectedGameName, outputFile!!)
                    outputFile = null

                    parentFragmentManager.setFragmentResult("invalidate", Bundle())

                    dismiss()
                }.start()
            }
        }

        return dialog
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)

        if (!calledSetup && operationType == OPERATION_SELECT_RAT) {
            calledSetup = true

            requireContext().sendBroadcast(
                Intent(ACTION_SETUP)
            )
        }
    }

    companion object {
        private var recyclerView: RecyclerView? = null
        private val fileList: MutableList<AdapterFiles.FileList> = mutableListOf()
        var calledSetup: Boolean = false
        var outputFile: String? = null
        var fmOperationType = -1

        const val OPERATION_SELECT_RAT = 0
        const val OPERATION_EXPORT_PRESET = 1
        const val OPERATION_IMPORT_PRESET = 2
        const val OPERATION_SELECT_EXE = 3

        fun refreshFiles() {
            recyclerView?.adapter?.notifyItemRangeRemoved(0, fileList.count())

            fileList.clear()

            if (fileManagerCwd != "/storage/emulated/0") {
                addToAdapter(File(".."))
            }

            File(fileManagerCwd!!).listFiles()?.sorted()?.forEach {
                if (it.isDirectory) {
                    addToAdapter(it)
                }
            }

            File(fileManagerCwd!!).listFiles()?.sorted()?.forEach {
                if (it.isFile) {
                    when (fmOperationType) {
                        OPERATION_SELECT_RAT -> {
                            if (it.name.endsWith(".rat")) {
                                addToAdapter(it)
                            }
                        }
                        OPERATION_IMPORT_PRESET -> {
                            if (it.name.endsWith(".mwp")) {
                                val mwpType = it.readLines()[0]

                                when (clickedPresetType) {
                                    CONTROLLER_PRESET -> {
                                        if (mwpType == "controllerPreset") {
                                            addToAdapter(it)
                                        }
                                    }
                                    VIRTUAL_CONTROLLER_PRESET -> {
                                        if (mwpType == "virtualControllerPreset") {
                                            addToAdapter(it)
                                        }
                                    }
                                    BOX64_PRESET -> {
                                        if (mwpType == "box64Preset") {
                                            addToAdapter(it)
                                        }
                                    }
                                }
                            }
                        }
                        OPERATION_SELECT_EXE -> {
                            if (it.name.endsWith(".exe")) {
                                addToAdapter(it)
                            }
                        }
                        else -> {
                            addToAdapter(it)
                        }
                    }
                }
            }

            recyclerView?.adapter?.notifyItemRangeInserted(0, fileList.count())
        }

        private fun addToAdapter(file: File) {
            fileList.add(
                AdapterFiles.FileList(file)
            )
        }
    }
}