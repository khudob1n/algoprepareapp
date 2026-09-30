# Project-specific R8 rules. kotlinx.serialization / Hilt / Compose ship consumer rules.

# Type-safe navigation looks enum route arguments up by their serial (class) name at runtime.
-keep class com.algoprep.app.domain.model.SessionType { *; }
