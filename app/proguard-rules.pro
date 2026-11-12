# App-specific R8 rules.
#
# AGP 9.4.1 / R8 strips the Glance composition path (provideGlance, sessions)
# and the WorkManager execution path (startWork/doWork), and resource shrinking
# then removes the artwork referenced only from the stripped code. Keep the app
# entry points and the library chains they drive.

# App code (widget receiver, Glance widget, artwork lookup, workers).
-keep class dev.stokonn.cocwidgt.** { *; }

# Glance: receivers/sessions/workers are driven from the manifest and by
# WorkManager; composition entry points are overridable framework hooks.
-keep class androidx.glance.** { *; }

# WorkManager: workers are instantiated by class name from the work database.
-keep class androidx.work.** { *; }
