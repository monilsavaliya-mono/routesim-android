# RouteSim proguard rules.
# Room, kotlinx.serialization and osmdroid ship consumer rules; nothing app-specific is required.
-keepattributes *Annotation*
-keep class com.routesim.app.data.db.entity.** { *; }
