package com.cmy.myfitnessapp

import android.app.Activity
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import com.cmy.myfitnessapp.PermissionConstants.GOOGLE_FIT_PERMISSIONS_REQUEST_CODE
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
        requestSessionAuth()
    }

    private fun requestSessionAuth() {
        val dataTypes =
            arrayListOf(
                DataType.TYPE_HEART_RATE_BPM,
                DataType.AGGREGATE_HEART_RATE_SUMMARY,
                DataType.AGGREGATE_STEP_COUNT_DELTA,
                DataType.TYPE_STEP_COUNT_DELTA,
            )

        val sessionFitnessOptions = getSessionFitnessOptions(dataTypes)

        val googleSignInAccount =
            GoogleSignIn.getAccountForExtension(this, sessionFitnessOptions)

        val isGranted =
            GoogleSignIn.hasPermissions(googleSignInAccount, sessionFitnessOptions)

        if (!isGranted) {
            GoogleSignIn.requestPermissions(
                this,
                GOOGLE_FIT_PERMISSIONS_REQUEST_CODE,
                googleSignInAccount,
                sessionFitnessOptions
            )
        } else {
            sendSessionsRequestAndGetResponse()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (resultCode) {
            Activity.RESULT_OK -> when (requestCode) {
                GOOGLE_FIT_PERMISSIONS_REQUEST_CODE -> sendAndGetResponse()
                else -> {
                    // Result wasn't from Google Fit
                }
            }
            else -> {
                // Permission not granted
            }
        }
    }

    private fun sendAndGetResponse() {
        val dataTypes =
            arrayListOf(DataType.TYPE_STEP_COUNT_DELTA)

        val sessionFitnessOptions = getHistoryFitnessOptions(dataTypes)

        val startTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LocalDate.now().atStartOfDay(ZoneId.systemDefault()).minusDays(10)
        } else {
            TODO("VERSION.SDK_INT < O")
        }
        val endTime = LocalDateTime.now().atZone(ZoneId.systemDefault())

        val sessionReadRequest = getHistoryReadRequest(
            startTime = startTime.toEpochSecond(),
            endTime = endTime.toEpochSecond()
        )

        val googleSignInAccount =
            GoogleSignIn.getAccountForExtension(this, sessionFitnessOptions)

        Fitness.getHistoryClient(this, googleSignInAccount)
            .readData(sessionReadRequest)
            .addOnSuccessListener { response ->
                val flatMap = response.buckets
                    .flatMap { it.dataSets }
                    .flatMap { it.dataPoints }
                val totalSteps = flatMap
                    .sumBy { it.getValue(Field.FIELD_STEPS).asInt() }
                print("")
            }
    }

    private fun sendSessionsRequestAndGetResponse() {
        val dataTypes =
            arrayListOf(DataType.TYPE_HEART_RATE_BPM)

        val sessionFitnessOptions = getSessionFitnessOptions(dataTypes)

        val queryStartTime = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LocalDate.now().atStartOfDay(ZoneId.systemDefault()).minusDays(10)
        } else {
            TODO("VERSION.SDK_INT < O")
        }
        val endTime = LocalDateTime.now().atZone(ZoneId.systemDefault())

        val sessionReadRequest = getSessionReadRequest(
            dataTypes = dataTypes,
            startTime = queryStartTime.toEpochSecond(),
            endTime = endTime.toEpochSecond()
        )

        val googleSignInAccount =
            GoogleSignIn.getAccountForExtension(this, sessionFitnessOptions)

        Fitness.getSessionsClient(this, googleSignInAccount)
            .readSession(sessionReadRequest)
            .addOnSuccessListener { response ->
                for (session in response.sessions) {
                    val sessionStart = session.getStartTime(TimeUnit.MILLISECONDS)
                    val sessionEnd = session.getEndTime(TimeUnit.MILLISECONDS)
                    Log.i("TAG", "za between $sessionStart and $sessionEnd")

                    val dataSets = response.getDataSet(session)
                    for (dataSet in dataSets) {
                        val firstDataPoint = dataSet.dataPoints[0]
                        var initialTimestamp = firstDataPoint.getStartTime(TimeUnit.MILLISECONDS)
                        var initialHeartRate = firstDataPoint.getValue(Field.FIELD_BPM).asFloat()

                        val intervals = dataSet.dataPoints.subList(1, dataSet.dataPoints.size).map {
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
                        val average =
                            intervals.sumOf { it.first.toDouble() * it.second } / intervals.sumOf { it.first }
                        print("")
                    }
                }
            }
    }

    private fun getSessionFitnessOptions(dataTypes: List<DataType>): FitnessOptions {
        val fitnessOptionsBuilder = FitnessOptions.builder()
            .accessActivitySessions(FitnessOptions.ACCESS_READ)

        dataTypes.forEach {
            fitnessOptionsBuilder.addDataType(it, FitnessOptions.ACCESS_READ)
        }
        return fitnessOptionsBuilder.build()
    }

    private fun getHistoryFitnessOptions(dataTypes: List<DataType>): FitnessOptions {
        val fitnessOptionsBuilder = FitnessOptions.builder()

        dataTypes.forEach {
            fitnessOptionsBuilder.addDataType(it, FitnessOptions.ACCESS_READ)
        }
        return fitnessOptionsBuilder.build()
    }

    private fun getSessionReadRequest(
        startTime: Long,
        endTime: Long,
        dataTypes: List<DataType>
    ): SessionReadRequest {
        val datasource = DataSource.Builder()
            .setAppPackageName("com.google.android.gms")
            .setDataType(DataType.TYPE_HEART_RATE_BPM)
            .setType(DataSource.TYPE_DERIVED)
            .build()

        val sessionReadRequestBuilder = SessionReadRequest.Builder()
            .includeActivitySessions()
            .enableServerQueries()
            .readSessionsFromAllApps()
            .read(DataType.TYPE_HEART_RATE_BPM)
            .setTimeInterval(startTime, endTime, TimeUnit.SECONDS)


        return sessionReadRequestBuilder
            .build()
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

        val request = DataReadRequest.Builder()
            .aggregate(datasource)
            .bucketByTime(1, TimeUnit.DAYS)
            .setTimeRange(startTime, endTime, TimeUnit.SECONDS)
            .build()

        return request
    }
}

object PermissionConstants {
    const val GOOGLE_FIT_PERMISSIONS_REQUEST_CODE = 1111
    const val ACTIVITY_RECOGNITION_REQUEST_CODE = 1112
}


