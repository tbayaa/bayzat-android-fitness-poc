package com.cmy.myfitnessapp

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.cmy.myfitnessapp.PermissionConstants.HISTORY_PERMISSIONS_REQUEST_CODE
import com.cmy.myfitnessapp.PermissionConstants.SESSION_PERMISSIONS_REQUEST_CODE
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.fitness.Fitness
import com.google.android.gms.fitness.FitnessOptions
import com.google.android.gms.fitness.data.DataSource
import com.google.android.gms.fitness.data.DataType
import com.google.android.gms.fitness.data.Field
import com.google.android.gms.fitness.request.DataReadRequest
import com.google.android.gms.fitness.request.SessionReadRequest
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        getSessionHeartRateInformation()
        getDailyStepInformation()
    }

    private fun getSessionHeartRateInformation() {
        val dataTypes =
            arrayListOf(
                DataType.TYPE_HEART_RATE_BPM,
            )
        val arePermissionsGranted = areSessionPermissionsGranted(dataTypes)

        if (arePermissionsGranted) {
            getSessionHeartRateAverage()
        } else {
            requestSessionPermissions(dataTypes)
        }
    }

    private fun areSessionPermissionsGranted(dataTypesOfPermissions: List<DataType>): Boolean {
        val fitnessOptions = getSessionFitnessOptions(dataTypesOfPermissions)
        val googleAccount = GoogleSignIn.getAccountForExtension(this, fitnessOptions)
        return GoogleSignIn.hasPermissions(googleAccount, fitnessOptions)
    }

    private fun requestSessionPermissions(dataTypesOfPermissions: List<DataType>) {
        val fitnessOptions = getSessionFitnessOptions(dataTypesOfPermissions)
        val googleAccount =
            GoogleSignIn.getAccountForExtension(this, fitnessOptions)

        GoogleSignIn.requestPermissions(
            this,
            SESSION_PERMISSIONS_REQUEST_CODE,
            googleAccount,
            fitnessOptions
        )
    }

    private fun getSessionHeartRateAverage() {
        val dataTypes =
            arrayListOf(DataType.TYPE_HEART_RATE_BPM)

        val fitnessOptions = getSessionFitnessOptions(dataTypes)

        val queryStartTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LocalDate.now().atStartOfDay(ZoneId.systemDefault()).minusDays(10)
        } else {
            TODO("VERSION.SDK_INT < O")
        }
        val endTime = LocalDateTime.now().atZone(ZoneId.systemDefault())

        val sessionReadRequest = getSessionHeartRateReadRequest(
            startTime = queryStartTime.toEpochSecond(),
            endTime = endTime.toEpochSecond()
        )

        val googleAccount = GoogleSignIn.getAccountForExtension(this, fitnessOptions)

        Fitness.getSessionsClient(this, googleAccount)
            .readSession(sessionReadRequest)
            .addOnSuccessListener { response ->
                for (session in response.sessions) {
                    val dataSets = response.getDataSet(session)
                    for (dataSet in dataSets) {
                        val dataPoints = dataSet.dataPoints

                        dataPoints.firstOrNull()?.let { firstDataPoint ->
                            var initialTimestamp =
                                firstDataPoint.getStartTime(TimeUnit.MILLISECONDS)
                            var initialHeartRate =
                                firstDataPoint.getValue(Field.FIELD_BPM).asFloat()

                            if (dataPoints.size > 1) {
                                val intervals = dataPoints.subList(1, dataPoints.size).map {
                                    val startTime = it.getStartTime(TimeUnit.MILLISECONDS)
                                    val heartRate = it.getValue(Field.FIELD_BPM).asFloat()
                                    val pair = Pair(
                                        first = startTime - initialTimestamp,
                                        second = (heartRate + initialHeartRate) / 2,
                                    )
                                    initialTimestamp = startTime
                                    initialHeartRate = heartRate
                                    pair
                                }
                                //TODO return it
                                val averageHeartRate =
                                    intervals.sumOf { it.first.toDouble() * it.second } / intervals.sumOf { it.first }
                            } else {
                                //TODO return it
                                val averageHeartRate = initialHeartRate
                            }
                        }
                    }
                }
            }
    }

    private fun getSessionHeartRateReadRequest(
        startTime: Long,
        endTime: Long,
    ): SessionReadRequest {
//        val datasource = DataSource.Builder()
//            .setAppPackageName("com.google.android.gms")
//            .setDataType(DataType.TYPE_HEART_RATE_BPM)
//            .setType(DataSource.TYPE_DERIVED)
//            .build()

        return SessionReadRequest.Builder()
            .includeActivitySessions()
            .enableServerQueries()
            .readSessionsFromAllApps()
            .read(DataType.TYPE_HEART_RATE_BPM)
            .setTimeInterval(startTime, endTime, TimeUnit.SECONDS)
            .build()
    }

    private fun getSessionFitnessOptions(dataTypes: List<DataType>): FitnessOptions {
        val fitnessOptionsBuilder = FitnessOptions.builder()
            .accessActivitySessions(FitnessOptions.ACCESS_READ)

        dataTypes.forEach {
            fitnessOptionsBuilder.addDataType(it, FitnessOptions.ACCESS_READ)
        }
        return fitnessOptionsBuilder.build()
    }

    private fun getDailyStepInformation() {
        val dataTypes = arrayListOf(DataType.TYPE_STEP_COUNT_DELTA)
        val arePermissionsGranted = areHistoryPermissionsGranted(dataTypes)

        if (arePermissionsGranted) {
            getDailyStepDataForTimeInterval()
        } else {
            requestHistoryPermissions(dataTypes)
        }
    }

    private fun areHistoryPermissionsGranted(dataTypesOfPermissions: List<DataType>): Boolean {
        val fitnessOptions = getHistoryFitnessOptions(dataTypesOfPermissions)
        val googleAccount = GoogleSignIn.getAccountForExtension(this, fitnessOptions)
        return GoogleSignIn.hasPermissions(googleAccount, fitnessOptions)
    }

    private fun requestHistoryPermissions(dataTypesOfPermissions: List<DataType>) {
        val fitnessOptions = getHistoryFitnessOptions(dataTypesOfPermissions)
        val googleAccount = GoogleSignIn.getAccountForExtension(this, fitnessOptions)

        GoogleSignIn.requestPermissions(
            this,
            SESSION_PERMISSIONS_REQUEST_CODE,
            googleAccount,
            fitnessOptions
        )
    }

    private fun getDailyStepDataForTimeInterval() {
        val dataTypes = arrayListOf(DataType.TYPE_STEP_COUNT_DELTA)
        val fitnessOptions = getHistoryFitnessOptions(dataTypes)

        val startTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LocalDate.now().atStartOfDay(ZoneId.systemDefault()).minusDays(10)
        } else {
            TODO("VERSION.SDK_INT < O")
        }
        val endTime = LocalDateTime.now().atZone(ZoneId.systemDefault())

        val historyReadRequest = getHistoryReadRequest(
            startTime = startTime.toEpochSecond(),
            endTime = endTime.toEpochSecond()
        )

        val googleAccount = GoogleSignIn.getAccountForExtension(this, fitnessOptions)

        Fitness.getHistoryClient(this, googleAccount)
            .readData(historyReadRequest)
            .addOnSuccessListener { response ->
                val flatMap = response.buckets
                    .flatMap { it.dataSets }
                    .flatMap { it.dataPoints }
                val totalSteps = flatMap
                    .sumBy { it.getValue(Field.FIELD_STEPS).asInt() }
                print("")
            }
    }

    private fun getHistoryReadRequest(
        startTime: Long,
        endTime: Long,
    ): DataReadRequest {
        val datasource = DataSource.Builder()
            .setAppPackageName("com.google.android.gms")
            .setDataType(DataType.TYPE_STEP_COUNT_DELTA)
            .setType(DataSource.TYPE_DERIVED)
            .setStreamName("estimated_steps")
            .build()

        return DataReadRequest.Builder()
            .aggregate(datasource)
            .bucketByTime(1, TimeUnit.DAYS)
            .setTimeRange(startTime, endTime, TimeUnit.SECONDS)
            .build()
    }

    private fun getHistoryFitnessOptions(dataTypes: List<DataType>): FitnessOptions {
        val fitnessOptionsBuilder = FitnessOptions.builder()

        dataTypes.forEach {
            fitnessOptionsBuilder.addDataType(it, FitnessOptions.ACCESS_READ)
        }
        return fitnessOptionsBuilder.build()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (resultCode) {
            Activity.RESULT_OK -> when (requestCode) {
                SESSION_PERMISSIONS_REQUEST_CODE -> getSessionHeartRateAverage()
                HISTORY_PERMISSIONS_REQUEST_CODE -> getDailyStepDataForTimeInterval()
                else -> {
                    // Result wasn't from Google Fit
                }
            }
            else -> {
                // Permission not granted
            }
        }
    }
}

object PermissionConstants {
    const val SESSION_PERMISSIONS_REQUEST_CODE = 1111
    const val HISTORY_PERMISSIONS_REQUEST_CODE = 1112
    const val ACTIVITY_RECOGNITION_REQUEST_CODE = 1113
}


