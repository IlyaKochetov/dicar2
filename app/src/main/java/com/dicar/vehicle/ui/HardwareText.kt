package com.dicar.vehicle.ui

import androidx.compose.runtime.Composable
import com.dicar.vehicle.R

/** Resolve known collector labels while retaining raw values for exports and redaction. */
@Composable
fun hardwareText(value: String): String = androidx.compose.ui.platform.LocalContext.current.hardwareText(value)

fun android.content.Context.hardwareText(value: String): String {
    val id = when (value) {
        "Current connection" -> R.string.hardware_current_connection
        "Type" -> R.string.hardware_type
        "Downlink bandwidth" -> R.string.hardware_downlink_bandwidth
        "Uplink bandwidth" -> R.string.hardware_uplink_bandwidth
        "Internet access verified" -> R.string.hardware_internet_access_verified
        "Metered network" -> R.string.hardware_metered_network
        "Wi-Fi" -> R.string.hardware_wi_fi
        "Hardware" -> R.string.hardware_hardware
        "Enabled" -> R.string.hardware_enabled
        "5 GHz" -> R.string.hardware_5_ghz
        "6 GHz" -> R.string.hardware_6_ghz
        "Wi-Fi 6" -> R.string.hardware_wi_fi_6
        "Wi-Fi 5" -> R.string.hardware_wi_fi_5
        "Wi-Fi Direct" -> R.string.hardware_wi_fi_direct
        "Wi-Fi Aware" -> R.string.hardware_wi_fi_aware
        "Wi-Fi RTT ranging" -> R.string.hardware_wi_fi_rtt_ranging
        "Link speed" -> R.string.hardware_link_speed
        "Frequency" -> R.string.hardware_frequency
        "Signal strength" -> R.string.hardware_signal_strength
        "IP address" -> R.string.hardware_ip_address
        "Mobile network" -> R.string.hardware_mobile_network
        "Network type" -> R.string.hardware_network_type
        "Carrier" -> R.string.hardware_carrier
        "SIM status" -> R.string.hardware_sim_status
        "eSIM" -> R.string.hardware_esim
        "Bluetooth" -> R.string.hardware_bluetooth
        "Bluetooth LE" -> R.string.hardware_bluetooth_le
        "BLE advertising" -> R.string.hardware_ble_advertising
        "LE Audio" -> R.string.hardware_le_audio
        "Other wireless" -> R.string.hardware_other_wireless
        "NFC" -> R.string.hardware_nfc
        "GPS" -> R.string.hardware_gps
        "Network location" -> R.string.hardware_network_location
        "USB host" -> R.string.hardware_usb_host
        "USB accessory" -> R.string.hardware_usb_accessory
        "Ethernet" -> R.string.hardware_ethernet
        "Network interfaces" -> R.string.hardware_network_interfaces
        "Output sample rate" -> R.string.hardware_output_sample_rate
        "Output buffer" -> R.string.hardware_output_buffer
        "Low-latency audio" -> R.string.hardware_low_latency_audio
        "Professional audio" -> R.string.hardware_professional_audio
        "Microphone" -> R.string.hardware_microphone
        "MIDI" -> R.string.hardware_midi
        "Unprocessed audio source" -> R.string.hardware_unprocessed_audio_source
        "Volume levels" -> R.string.hardware_volume_levels
        "Output devices" -> R.string.hardware_output_devices
        "Input devices" -> R.string.hardware_input_devices
        "Resolution" -> R.string.hardware_resolution
        "Pixel density" -> R.string.hardware_pixel_density
        "Measured density" -> R.string.hardware_measured_density
        "Diagonal" -> R.string.hardware_diagonal
        "Scale factor" -> R.string.hardware_scale_factor
        "Logical size" -> R.string.hardware_logical_size
        "Smallest width" -> R.string.hardware_smallest_width
        "Screen category" -> R.string.hardware_screen_category
        "Refresh and color" -> R.string.hardware_refresh_and_color
        "Current refresh rate" -> R.string.hardware_current_refresh_rate
        "Display modes" -> R.string.hardware_display_modes
        "Wide color gamut" -> R.string.hardware_wide_color_gamut
        "HDR" -> R.string.hardware_hdr
        "HDR formats" -> R.string.hardware_hdr_formats
        "Night mode" -> R.string.hardware_night_mode
        "Status" -> R.string.hardware_status
        "Orientation" -> R.string.hardware_orientation
        "Display state" -> R.string.hardware_display_state
        "Brightness" -> R.string.hardware_brightness
        "Brightness mode" -> R.string.hardware_brightness_mode
        "Notch / cutout" -> R.string.hardware_notch_cutout
        "Other displays" -> R.string.hardware_other_displays
        "Touch" -> R.string.hardware_touch
        "Touchscreen" -> R.string.hardware_touchscreen
        "Multitouch" -> R.string.hardware_multitouch
        "Pressure sensitivity" -> R.string.hardware_pressure_sensitivity
        "Scan failed" -> R.string.hardware_scan_failed
        "Battery" -> R.string.hardware_battery
        "Battery present" -> R.string.hardware_battery_present
        "Battery level" -> R.string.hardware_battery_level
        "Health" -> R.string.hardware_health
        "Power source" -> R.string.hardware_power_source
        "Readings" -> R.string.hardware_readings
        "Voltage" -> R.string.hardware_voltage
        "Temperature" -> R.string.hardware_temperature
        "Instantaneous current" -> R.string.hardware_instantaneous_current
        "Average current" -> R.string.hardware_average_current
        "Remaining charge" -> R.string.hardware_remaining_charge
        "Remaining energy" -> R.string.hardware_remaining_energy
        "Estimated full capacity" -> R.string.hardware_estimated_full_capacity
        "Cycle count" -> R.string.hardware_cycle_count
        "System thermal status" -> R.string.hardware_system_thermal_status
        "Thermal zones" -> R.string.hardware_thermal_zones
        "Maximum temperature" -> R.string.hardware_maximum_temperature
        "Note" -> R.string.hardware_note
        "Cores" -> R.string.hardware_cores
        "Model" -> R.string.hardware_model
        "Codec count" -> R.string.hardware_codec_count
        "Video" -> R.string.hardware_video
        "Audio" -> R.string.hardware_audio
        "Hardware accelerated" -> R.string.hardware_hardware_accelerated
        "Software only" -> R.string.hardware_software_only
        "Digital rights (DRM)" -> R.string.hardware_digital_rights_drm
        "Widevine" -> R.string.hardware_widevine
        "PlayReady" -> R.string.hardware_playready
        "ClearKey" -> R.string.hardware_clearkey
        "Total capacity" -> R.string.hardware_total_capacity
        "Nominal capacity" -> R.string.hardware_nominal_capacity
        "Memory type" -> R.string.hardware_memory_type
        "Low-memory device" -> R.string.hardware_low_memory_device
        "Low-memory threshold" -> R.string.hardware_low_memory_threshold
        "Details (/proc/meminfo)" -> R.string.hardware_details_proc_meminfo
        "Swap and ZRAM" -> R.string.hardware_swap_and_zram
        "Total swap" -> R.string.hardware_total_swap
        "Free swap" -> R.string.hardware_free_swap
        "ZRAM devices" -> R.string.hardware_zram_devices
        "App heap limits" -> R.string.hardware_app_heap_limits
        "Standard heap limit" -> R.string.hardware_standard_heap_limit
        "Large heap limit" -> R.string.hardware_large_heap_limit
        "Process heap limit" -> R.string.hardware_process_heap_limit
        "Dalvik heap size" -> R.string.hardware_dalvik_heap_size
        "Dalvik growth limit" -> R.string.hardware_dalvik_growth_limit
        "Dalvik initial heap" -> R.string.hardware_dalvik_initial_heap
        "Capacity" -> R.string.hardware_capacity
        "Storage volumes" -> R.string.hardware_storage_volumes
        "Physical storage media" -> R.string.hardware_physical_storage_media
        "Mounted file systems" -> R.string.hardware_mounted_file_systems
        "Mount count" -> R.string.hardware_mount_count
        "Count" -> R.string.hardware_count
        "Wake-up sensor" -> R.string.hardware_wake_up_sensor
        "Vendor" -> R.string.hardware_vendor
        "Type ID" -> R.string.hardware_type_id
        "Version" -> R.string.hardware_version
        "Range" -> R.string.hardware_range
        "Power draw" -> R.string.hardware_power_draw
        "Maximum frequency" -> R.string.hardware_maximum_frequency
        "Maximum interval" -> R.string.hardware_maximum_interval
        "Reporting mode" -> R.string.hardware_reporting_mode
        "Batch buffer" -> R.string.hardware_batch_buffer
        "Sensor ID" -> R.string.hardware_sensor_id
        "Direct report level" -> R.string.hardware_direct_report_level
        "ID list" -> R.string.hardware_id_list
        "Maximum resolution" -> R.string.hardware_maximum_resolution
        "Pixel array" -> R.string.hardware_pixel_array
        "Active area" -> R.string.hardware_active_area
        "Sensor size" -> R.string.hardware_sensor_size
        "Focal lengths" -> R.string.hardware_focal_lengths
        "Apertures" -> R.string.hardware_apertures
        "Maximum digital zoom" -> R.string.hardware_maximum_digital_zoom
        "ISO range" -> R.string.hardware_iso_range
        "Exposure time" -> R.string.hardware_exposure_time
        "Flash" -> R.string.hardware_flash
        "Mounting angle" -> R.string.hardware_mounting_angle
        "Hardware level" -> R.string.hardware_hardware_level
        "Focus modes" -> R.string.hardware_focus_modes
        "Supported formats" -> R.string.hardware_supported_formats
        "Video capabilities" -> R.string.hardware_video_capabilities
        "Logical multicamera" -> R.string.hardware_logical_multicamera
        "Platform codename" -> R.string.hardware_platform_codename
        "Manufacturer" -> R.string.hardware_manufacturer
        "CPUinfo identifier" -> R.string.hardware_cpuinfo_identifier
        "x86 model string" -> R.string.hardware_x86_model_string
        "CPU cores" -> R.string.hardware_cpu_cores
        "Cores available to process" -> R.string.hardware_cores_available_to_process
        "Instruction sets" -> R.string.hardware_instruction_sets
        "Primary ABI" -> R.string.hardware_primary_abi
        "64-bit ABI" -> R.string.hardware_64_bit_abi
        "32-bit ABI" -> R.string.hardware_32_bit_abi
        "Bitness" -> R.string.hardware_bitness
        "Architecture version" -> R.string.hardware_architecture_version
        "Extension count" -> R.string.hardware_extension_count
        "Extensions" -> R.string.hardware_extensions
        "Notable extensions" -> R.string.hardware_notable_extensions
        "CPU clusters" -> R.string.hardware_cpu_clusters
        "Frequency scaling" -> R.string.hardware_frequency_scaling
        "Current governor" -> R.string.hardware_current_governor
        "Available governors" -> R.string.hardware_available_governors
        "Driver" -> R.string.hardware_driver
        "Available frequencies" -> R.string.hardware_available_frequencies
        "Board" -> R.string.hardware_board
        "Board name" -> R.string.hardware_board_name
        "Hardware codename" -> R.string.hardware_hardware_codename
        "Bootloader" -> R.string.hardware_bootloader
        "DDR type" -> R.string.hardware_ddr_type
        "Can run 32-bit apps" -> R.string.hardware_can_run_32_bit_apps
        "OpenGL ES" -> R.string.hardware_opengl_es
        "Shader language" -> R.string.hardware_shader_language
        "Reported API version" -> R.string.hardware_reported_api_version
        "Capability limits" -> R.string.hardware_capability_limits
        "Maximum texture size" -> R.string.hardware_maximum_texture_size
        "Maximum cube-map size" -> R.string.hardware_maximum_cube_map_size
        "Maximum viewport" -> R.string.hardware_maximum_viewport
        "Maximum renderbuffer" -> R.string.hardware_maximum_renderbuffer
        "Vertex attributes" -> R.string.hardware_vertex_attributes
        "Texture units (fragment)" -> R.string.hardware_texture_units_fragment
        "Texture units (total)" -> R.string.hardware_texture_units_total
        "Anisotropic filtering" -> R.string.hardware_anisotropic_filtering
        "Maximum multisampling" -> R.string.hardware_maximum_multisampling
        "Compressed texture formats" -> R.string.hardware_compressed_texture_formats
        "ASTC textures" -> R.string.hardware_astc_textures
        "ETC2 textures" -> R.string.hardware_etc2_textures
        "Floating-point textures" -> R.string.hardware_floating_point_textures
        "Full list" -> R.string.hardware_full_list
        "Vulkan" -> R.string.hardware_vulkan
        "Hardware supported" -> R.string.hardware_hardware_supported
        "Compute capability" -> R.string.hardware_compute_capability
        "Other" -> R.string.hardware_other
        "AEP extension pack" -> R.string.hardware_aep_extension_pack
        "Frequency node" -> R.string.hardware_frequency_node
        "Device" -> R.string.hardware_device
        "Brand" -> R.string.hardware_brand
        "Device codename" -> R.string.hardware_device_codename
        "Product name" -> R.string.hardware_product_name
        "Marketing name" -> R.string.hardware_marketing_name
        "Serial number" -> R.string.hardware_serial_number
        "Android" -> R.string.hardware_android
        "API level" -> R.string.hardware_api_level
        "Security patch" -> R.string.hardware_security_patch
        "Codename" -> R.string.hardware_codename
        "Build ID" -> R.string.hardware_build_id
        "Build display" -> R.string.hardware_build_display
        "Incremental build" -> R.string.hardware_incremental_build
        "Build type" -> R.string.hardware_build_type
        "Build date" -> R.string.hardware_build_date
        "Build fingerprint" -> R.string.hardware_build_fingerprint
        "Kernel and runtime" -> R.string.hardware_kernel_and_runtime
        "Kernel version" -> R.string.hardware_kernel_version
        "Full kernel string" -> R.string.hardware_full_kernel_string
        "Java VM" -> R.string.hardware_java_vm
        "ART/Dalvik" -> R.string.hardware_art_dalvik
        "64-bit Zygote" -> R.string.hardware_64_bit_zygote
        "Runtime platform" -> R.string.hardware_runtime_platform
        "Clock frequency" -> R.string.hardware_clock_frequency
        "Security and integrity" -> R.string.hardware_security_and_integrity
        "SELinux" -> R.string.hardware_selinux
        "Debuggable build" -> R.string.hardware_debuggable_build
        "Release signed" -> R.string.hardware_release_signed
        "Root (su) detected" -> R.string.hardware_root_su_detected
        "su path" -> R.string.hardware_su_path
        "ADB enabled" -> R.string.hardware_adb_enabled
        "Storage encryption" -> R.string.hardware_storage_encryption
        "Verified boot" -> R.string.hardware_verified_boot
        "Treble" -> R.string.hardware_treble
        "VNDK version" -> R.string.hardware_vndk_version
        "Runtime state" -> R.string.hardware_runtime_state
        "Uptime" -> R.string.hardware_uptime
        "Awake time" -> R.string.hardware_awake_time
        "Deep sleep" -> R.string.hardware_deep_sleep
        "Last boot" -> R.string.hardware_last_boot
        "Time zone" -> R.string.hardware_time_zone
        "Language" -> R.string.hardware_language
        "External storage state" -> R.string.hardware_external_storage_state
        "Software environment" -> R.string.hardware_software_environment
        "Installed apps" -> R.string.hardware_installed_apps
        "System apps" -> R.string.hardware_system_apps
        "WebView" -> R.string.hardware_webview
        "Google Play services" -> R.string.hardware_google_play_services
        "Shared libraries" -> R.string.hardware_shared_libraries
        "Head unit" -> R.string.hardware_head_unit
        "BYDAuto framework" -> R.string.hardware_bydauto_framework
        "DiLink version" -> R.string.hardware_dilink_version
        "Vehicle model code" -> R.string.hardware_vehicle_model_code
        "BYD properties" -> R.string.hardware_byd_properties
        "Unnamed features" -> R.string.hardware_unnamed_features
        "All features" -> R.string.hardware_all_features
        "Processor" -> R.string.hardware_processor
        "Graphics" -> R.string.hardware_graphics
        "Memory" -> R.string.hardware_memory
        "Storage" -> R.string.hardware_storage
        "Display" -> R.string.hardware_display
        "Power" -> R.string.hardware_power
        "Thermal" -> R.string.hardware_thermal
        "Sensors" -> R.string.hardware_sensors
        "Cameras" -> R.string.hardware_cameras
        "Network" -> R.string.hardware_network
        "Codecs" -> R.string.hardware_codecs
        "System" -> R.string.hardware_system
        "Features" -> R.string.hardware_features
        "Properties" -> R.string.hardware_properties
        "Unknown" -> R.string.hardware_unknown
        "Supported" -> R.string.hardware_supported
        "Unsupported" -> R.string.hardware_unsupported
        "Yes" -> R.string.hardware_yes
        "No" -> R.string.hardware_no
        "Disabled" -> R.string.hardware_disabled
        "On" -> R.string.hardware_value_on
        "Off" -> R.string.hardware_value_off
        "Built-in speaker" -> R.string.hardware_value_built_in_speaker
        "Earpiece" -> R.string.hardware_value_earpiece
        "Built-in microphone" -> R.string.hardware_value_built_in_microphone
        "Wired headset" -> R.string.hardware_value_wired_headset
        "Wired headphones" -> R.string.hardware_value_wired_headphones
        "Bluetooth audio" -> R.string.hardware_value_bluetooth_audio
        "Bluetooth call audio" -> R.string.hardware_value_bluetooth_call_audio
        "USB device" -> R.string.hardware_value_usb_device
        "USB headset" -> R.string.hardware_value_usb_headset
        "Analog line" -> R.string.hardware_value_analog_line
        "Digital line" -> R.string.hardware_value_digital_line
        "FM radio" -> R.string.hardware_value_fm_radio
        "FM tuner" -> R.string.hardware_value_fm_tuner
        "Telephony" -> R.string.hardware_value_telephony
        "Vehicle bus" -> R.string.hardware_value_vehicle_bus
        "Remote submix" -> R.string.hardware_value_remote_submix
        "Bluetooth tethering" -> R.string.hardware_value_bluetooth_tethering
        "USB tethering" -> R.string.hardware_value_usb_tethering
        "None" -> R.string.hardware_value_none
        "No SIM" -> R.string.hardware_value_no_sim
        "Ready" -> R.string.hardware_value_ready
        "PIN required" -> R.string.hardware_value_pin_required
        "PUK required" -> R.string.hardware_value_puk_required
        "Network locked" -> R.string.hardware_value_network_locked
        "Not ready" -> R.string.hardware_value_not_ready
        "Permanently disabled" -> R.string.hardware_value_permanently_disabled
        "SIM I/O error" -> R.string.hardware_value_sim_i_o_error
        "Restricted" -> R.string.hardware_value_restricted
        "Automatic" -> R.string.hardware_value_automatic
        "Manual" -> R.string.hardware_value_manual
        "Supported (5+ points)" -> R.string.hardware_value_supported_5_points
        "Supported (2 independent points)" -> R.string.hardware_value_supported_2_independent_points
        "Supported (basic)" -> R.string.hardware_value_supported_basic
        "Extra-high density" -> R.string.hardware_value_extra_high_density
        "Small" -> R.string.hardware_value_small
        "Normal" -> R.string.hardware_value_normal
        "Large" -> R.string.hardware_value_large
        "Extra large" -> R.string.hardware_value_extra_large
        "Doze mode" -> R.string.hardware_value_doze_mode
        "On (suspended)" -> R.string.hardware_value_on_suspended
        "Light heating" -> R.string.hardware_value_light_heating
        "Moderate heating" -> R.string.hardware_value_moderate_heating
        "Severe heating" -> R.string.hardware_value_severe_heating
        "Critical" -> R.string.hardware_value_critical
        "Emergency throttling" -> R.string.hardware_value_emergency_throttling
        "Shutdown imminent" -> R.string.hardware_value_shutdown_imminent
        "Charging" -> R.string.hardware_value_charging
        "Discharging" -> R.string.hardware_value_discharging
        "Full" -> R.string.hardware_value_full
        "Not charging" -> R.string.hardware_value_not_charging
        "Good" -> R.string.hardware_value_good
        "Overheated" -> R.string.hardware_value_overheated
        "Dead" -> R.string.hardware_value_dead
        "Overvoltage" -> R.string.hardware_value_overvoltage
        "Too cold" -> R.string.hardware_value_too_cold
        "Failure" -> R.string.hardware_value_failure
        "Not connected" -> R.string.hardware_value_not_connected
        "AC power" -> R.string.hardware_value_ac_power
        "Wireless charging" -> R.string.hardware_value_wireless_charging
        "Software" -> R.string.hardware_value_software
        "No digital output" -> R.string.hardware_value_no_digital_output
        "Removable" -> R.string.hardware_value_removable
        "Built-in" -> R.string.hardware_value_built_in
        "read-only" -> R.string.hardware_value_read_only
        "writable" -> R.string.hardware_value_writable
        "On change only" -> R.string.hardware_value_on_change_only
        "One-shot" -> R.string.hardware_value_one_shot
        "Front" -> R.string.hardware_value_front
        "Rear" -> R.string.hardware_value_rear
        "External" -> R.string.hardware_value_external
        "Continuous" -> R.string.hardware_value_continuous
        "On change" -> R.string.hardware_value_on_change
        "Special trigger" -> R.string.hardware_value_special_trigger
        "Accelerometer" -> R.string.hardware_value_accelerometer
        "Magnetometer" -> R.string.hardware_value_magnetometer
        "Gyroscope" -> R.string.hardware_value_gyroscope
        "Light sensor" -> R.string.hardware_value_light_sensor
        "Barometer" -> R.string.hardware_value_barometer
        "Proximity sensor" -> R.string.hardware_value_proximity_sensor
        "Gravity" -> R.string.hardware_value_gravity
        "Linear acceleration" -> R.string.hardware_value_linear_acceleration
        "Rotation vector" -> R.string.hardware_value_rotation_vector
        "Relative humidity" -> R.string.hardware_value_relative_humidity
        "Ambient temperature" -> R.string.hardware_value_ambient_temperature
        "Magnetometer (uncalibrated)" -> R.string.hardware_value_magnetometer_uncalibrated
        "Game rotation vector" -> R.string.hardware_value_game_rotation_vector
        "Gyroscope (uncalibrated)" -> R.string.hardware_value_gyroscope_uncalibrated
        "Significant motion" -> R.string.hardware_value_significant_motion
        "Step detector" -> R.string.hardware_value_step_detector
        "Step counter" -> R.string.hardware_value_step_counter
        "Geomagnetic rotation vector" -> R.string.hardware_value_geomagnetic_rotation_vector
        "Heart rate" -> R.string.hardware_value_heart_rate
        "Stationary detection" -> R.string.hardware_value_stationary_detection
        "Motion detection" -> R.string.hardware_value_motion_detection
        "Heart beat" -> R.string.hardware_value_heart_beat
        "Off-body detection" -> R.string.hardware_value_off_body_detection
        "Accelerometer (uncalibrated)" -> R.string.hardware_value_accelerometer_uncalibrated
        "Hinge angle" -> R.string.hardware_value_hinge_angle
        "Sensor" -> R.string.hardware_value_sensor
        "Installed" -> R.string.hardware_value_installed
        "Not detected" -> R.string.hardware_value_not_detected
        "Restricted by Android" -> R.string.hardware_value_restricted_by_android
        "Enforcing" -> R.string.hardware_value_enforcing
        "Permissive" -> R.string.hardware_value_permissive
        "Encrypted" -> R.string.hardware_value_encrypted
        "Unencrypted" -> R.string.hardware_value_unencrypted
        "File-based encryption" -> R.string.hardware_value_file_based_encryption
        "Block-based encryption" -> R.string.hardware_value_block_based_encryption
        "6-DoF pose" -> R.string.hardware_value_six_dof_pose
        "32-bit" -> R.string.hardware_value_32_bit
        "LEGACY (legacy API only)" -> R.string.hardware_value_legacy
        "System did not provide battery information" -> R.string.hardware_no_battery_info
        "System restricts access to /sys/class/thermal on newer Android versions" -> R.string.hardware_thermal_restricted
        "Read failed (getprop unavailable)" -> R.string.hardware_getprop_failed
        "No details" -> R.string.hardware_no_details
        else -> null
    }
    return if (id == null) hardwareDynamic(value) else getString(id)
}

private fun android.content.Context.hardwareDynamic(value: String): String {
    val reading = Regex("^(-?\\d+(?:\\.\\d+)?) (GHz|MHz|kHz|Hz|Gbps|Mbps|kbps|KB|MB|GB|TB|PB|B|mV|mA|µA|℃)$").matchEntire(value)
    if (reading != null) {
        val unit = when (reading.groupValues[2]) {
            "GHz" -> R.string.unit_ghz
            "MHz" -> R.string.unit_mhz
            "kHz" -> R.string.unit_khz
            "Hz" -> R.string.unit_hz
            "Gbps" -> R.string.unit_gbps
            "Mbps" -> R.string.unit_mbps
            "kbps" -> R.string.unit_kbps
            "KB" -> R.string.unit_kilobyte
            "MB" -> R.string.unit_megabyte
            "GB" -> R.string.unit_gigabyte
            "TB" -> R.string.unit_terabyte
            "PB" -> R.string.unit_petabyte
            "B" -> R.string.unit_bytes
            "mV" -> R.string.unit_millivolt
            "mA" -> R.string.unit_milliamp
            "µA" -> R.string.unit_microamp
            else -> R.string.unit_celsius
        }
        return reading.groupValues[1] + " " + getString(unit)
    }
    val usage = Regex("^(-?\\d+(?:\\.\\d+)? (?:KB|MB|GB|TB|PB|B)) / (-?\\d+(?:\\.\\d+)? (?:KB|MB|GB|TB|PB|B))(\\(\\d+(?:\\.\\d+)?%\\))$").matchEntire(value)
    if (usage != null) return hardwareDynamic(usage.groupValues[1]) + " / " + hardwareDynamic(usage.groupValues[2]) + usage.groupValues[3]

    val days = Regex("^(\\d+) d (\\d+) h (\\d+) min$").matchEntire(value)
    if (days != null) return getString(R.string.hardware_duration_days_hours_minutes, days.groupValues[1].toIntOrNull() ?: return value, days.groupValues[2].toIntOrNull() ?: return value, days.groupValues[3].toIntOrNull() ?: return value)
    val hours = Regex("^(\\d+) h (\\d+) min$").matchEntire(value)
    if (hours != null) return getString(R.string.hardware_duration_hours_minutes, hours.groupValues[1].toIntOrNull() ?: return value, hours.groupValues[2].toIntOrNull() ?: return value)
    val minutes = Regex("^(\\d+) min$").matchEntire(value)
    if (minutes != null) return getString(R.string.hardware_duration_minutes, minutes.groupValues[1].toIntOrNull() ?: return value)
    val seconds = Regex("^(\\d+) sec$").matchEntire(value)
    if (seconds != null) return getString(R.string.hardware_duration_seconds, seconds.groupValues[1])
    val camera = Regex("^Camera (.*?) \\((.*?)\\)$").matchEntire(value)
    if (camera != null) return getString(R.string.hardware_camera_label, camera.groupValues[1], hardwareText(camera.groupValues[2]))
    val numberLabel = Regex("^(Type|Mode|Level|Version) (\\d+)$").matchEntire(value)
    if (numberLabel != null) {
        val id = when (numberLabel.groupValues[1]) {
            "Type" -> R.string.hardware_type_number
            "Mode" -> R.string.hardware_mode_number
            "Level" -> R.string.hardware_level_number
            else -> R.string.hardware_version_number
        }
        return getString(id, numberLabel.groupValues[2])
    }
    val inches = Regex("^([0-9.]+) inches$").matchEntire(value)
    if (inches != null) return getString(R.string.hardware_inches, inches.groupValues[1])
    val approx = Regex("^approx\\. (\\d+) mAh$").matchEntire(value)
    if (approx != null) return getString(R.string.hardware_approx_charge, approx.groupValues[1])
    val visibleApps = Regex("^(\\d+ apps) \\(visible to this app only\\)$").matchEntire(value)
    if (visibleApps != null) return getString(R.string.hardware_visible_apps, hardwareDynamic(visibleApps.groupValues[1]))
    val glItems = Regex("^(\\d+ items) \\(OpenGL ES versions\\)$").matchEntire(value)
    if (glItems != null) return getString(R.string.hardware_opengl_items, hardwareDynamic(glItems.groupValues[1]))
    val mount = Regex("^(.*?)（(read-only|writable)）$").matchEntire(value)
    if (mount != null) return getString(R.string.hardware_mount, mount.groupValues[1], hardwareText(mount.groupValues[2]))
    val sensor = Regex("^(.*?)（(.*?)）$").matchEntire(value)
    if (sensor != null) return getString(R.string.hardware_sensor_label, hardwareText(sensor.groupValues[1]), sensor.groupValues[2])
    val thermalCount = Regex("^Thermal zones \\((\\d+)\\)$").matchEntire(value)
    if (thermalCount != null) return getString(R.string.hardware_count_thermal, hardwareDynamic(thermalCount.groupValues[1] + " zones"))
    val count = Regex("^(\\d+) (cores|items|entries|zones|devices|modes|codecs|sensors|cameras|mounts|libraries|apps|types|events|frames|regions)$").matchEntire(value)
    if (count != null) {
        val number = count.groupValues[1].toIntOrNull() ?: return value
        val resource = when (count.groupValues[2]) {
            "cores" -> R.plurals.hardware_count_cores
            "items" -> R.plurals.hardware_count_items
            "entries" -> R.plurals.hardware_count_entries
            "zones" -> R.plurals.hardware_count_zones
            "devices" -> R.plurals.hardware_count_devices
            "modes" -> R.plurals.hardware_count_modes
            "codecs" -> R.plurals.hardware_count_codecs
            "sensors" -> R.plurals.hardware_count_sensors
            "cameras" -> R.plurals.hardware_count_cameras
            "mounts" -> R.plurals.hardware_count_mounts
            "libraries" -> R.plurals.hardware_count_libraries
            "apps" -> R.plurals.hardware_count_apps
            "types" -> R.plurals.hardware_count_types
            "events" -> R.plurals.hardware_count_events
            "frames" -> R.plurals.hardware_count_frames
            "regions" -> R.plurals.hardware_count_regions
            else -> return value
        }
        return resources.getQuantityString(resource, number, number)
    }
    val codecs = Regex("^(\\d+) decoders / (\\d+) encoders$").matchEntire(value)
    if (codecs != null) {
        val decoders = codecs.groupValues[1].toIntOrNull() ?: return value
        val encoders = codecs.groupValues[2].toIntOrNull() ?: return value
        return getString(R.string.hardware_count_codec_summary,
            resources.getQuantityString(R.plurals.hardware_count_decoders, decoders, decoders),
            resources.getQuantityString(R.plurals.hardware_count_encoders, encoders, encoders))
    }
    val present = Regex("^Present \\((\\d+ regions)\\)$").matchEntire(value)
    if (present != null) return getString(R.string.hardware_count_present, hardwareDynamic(present.groupValues[1]))
    val thermal = Regex("^Thermal zones \\((\\d+ zones?)\\)$").matchEntire(value)
    if (thermal != null) return getString(R.string.hardware_count_thermal, hardwareDynamic(thermal.groupValues[1]))
    val entries = Regex("^(.*?) \\((\\d+ entries)\\)$").matchEntire(value)
    if (entries != null) return getString(R.string.hardware_count_label, hardwareText(entries.groupValues[1]), hardwareDynamic(entries.groupValues[2]))
    return value
}

