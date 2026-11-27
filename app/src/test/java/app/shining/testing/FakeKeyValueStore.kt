package app.shining.testing

import app.shining.domain.model.KeyValueStore

class FakeKeyValueStore(initial: Map<String, String> = emptyMap()) : KeyValueStore {
    val values = initial.toMutableMap()

    override fun getString(key: String): String? = values[key]

    override fun putString(key: String, value: String) {
        values[key] = value
    }

    override fun remove(key: String) {
        values.remove(key)
    }
}
