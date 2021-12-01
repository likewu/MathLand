package tech.ula.utils

import android.app.AlertDialog
import android.app.Dialog
import android.content.Context
import android.content.SharedPreferences
import android.view.View
import androidx.annotation.IdRes
import androidx.lifecycle.LiveData
import androidx.lifecycle.MediatorLiveData
import cn.leafcolor.mathland.R

fun <A, B> zipLiveData(a: LiveData<A>, b: LiveData<B>): LiveData<Pair<A, B>> {
    return MediatorLiveData<Pair<A, B>>().apply {
        var lastA: A? = null
        var lastB: B? = null

        fun update() {
            val localLastA = lastA
            val localLastB = lastB
            if (localLastA != null && localLastB != null)
                this.value = Pair(localLastA, localLastB)
        }

        addSource(a) {
            lastA = it
            update()
        }
        addSource(b) {
            lastB = it
            update()
        }
    }
}

fun Context.displayGenericErrorDialog(titleId: Int, messageId: Int, callback: (() -> Unit) = {}) {
    AlertDialog.Builder(this)
            .setTitle(titleId)
            .setMessage(messageId)
            .setPositiveButton(R.string.button_ok) {
                dialog, _ ->
                callback()
                dialog.dismiss()
            }
            .create().show()
}

inline val Context.defaultSharedPreferences: SharedPreferences
    get() = this.getSharedPreferences("${this.packageName}_preferences", Context.MODE_PRIVATE)

inline fun <reified T : View> View.find(@IdRes id: Int): T = findViewById(id)
inline fun <reified T : View> Dialog.find(@IdRes id: Int): T = findViewById(id)