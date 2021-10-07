package tech.ula.ui

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import tech.ula.R
import tech.ula.utils.ProotDebugLogger
import tech.ula.utils.UlaFiles
import tech.ula.utils.defaultSharedPreferences
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.Charset
import java.util.*

class SettingsFragment : PreferenceFragmentCompat() {

    private val prootDebugLogger by lazy {
        val ulaFiles = UlaFiles(activity!!, activity!!.applicationInfo.nativeLibraryDir)
        ProotDebugLogger(activity!!.defaultSharedPreferences, ulaFiles)
    }

    @UseExperimental(ExperimentalStdlibApi::class)
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        addPreferencesFromResource(R.xml.preferences)

        val deleteFilePreference: Preference = findPreference("pref_proot_delete_debug_file")!!
        deleteFilePreference.setOnPreferenceClickListener {
            prootDebugLogger.deleteLogs()
            true
        }

        val home_path = context!!.filesDir.absolutePath + "/home"
        var propsFile: File = File(home_path + "/.termux/termux.properties")
        //if (!propsFile.exists()) propsFile = File(home_path + "/.config/termux/termux.properties")

        val termuxDirectory = File("$home_path/.termux")
        if (!termuxDirectory.exists()) termuxDirectory.mkdirs()

        var ss: String? = null
        try {
            if (propsFile.isFile && propsFile.canRead()) {
                val in1 = FileInputStream(propsFile)
                in1.reader(Charset.forName("utf-8")).use {
                    val buffer = CharArray(1000)
                    var n: Int
                    while (it.read(buffer) != -1) {
                        Log.i("settings", "read ${buffer.concatToString()} .")
                    }
                    ss = buffer.concatToString()
                }
            } else
                ss = ""
        } catch (e: IOException) {
            Toast.makeText(context, "Could not read the propertiey file termux.properties.", Toast.LENGTH_LONG).show()
            Log.e("settings", "Error loading termux.properties", e)
        }

        val termuxpropertiesPreference: EditTextPreference = findPreference<EditTextPreference>("pref_termux_properties")!!
        //termuxpropertiesPreference.isPersistent = false
        termuxpropertiesPreference.setText(ss)

        val termuxpropertiessavePreference: Preference = findPreference("pref_termux_properties_save")!!
        termuxpropertiessavePreference.setOnPreferenceClickListener {
            try {
                propsFile.writeText(termuxpropertiesPreference.getText(), Charset.forName("utf-8"))
                /*val in1 = FileOutputStream(propsFile)
                in1.writer(Charset.forName("utf-8")).use {
                    var n: Int
                    it.write(termuxpropertiesPreference.getText().toCharArray())
                }*/
                Toast.makeText(context, "save termux.properties ok.", Toast.LENGTH_LONG).show()
            } catch (e: IOException) {
                Toast.makeText(context, "Could not write the propertiey file termux.properties.", Toast.LENGTH_LONG).show()
                Log.e("settings", "Error writing termux.properties", e)
            }
            true
        }

        /*val context = preferenceManager.context
        val screen = preferenceManager.createPreferenceScreen(context)
        
        val category1 = PreferenceCategory(context)
        category1.title = "termux"
        category1.key = "pref_termux_category"
        category1.isIconSpaceReserved = false

        val termuxproperties = EditTextPreference(context)
        termuxproperties.title = "termux.properties"
        termuxproperties.setDefaultValue(ss)

        screen.addPreference(category1)
        category1.addPreference(termuxproperties)*/
    }

    override fun setDivider(divider: Drawable?) {
        super.setDivider(ColorDrawable(Color.TRANSPARENT))
    }

    override fun setDividerHeight(height: Int) {
        super.setDividerHeight(0)
    }
}