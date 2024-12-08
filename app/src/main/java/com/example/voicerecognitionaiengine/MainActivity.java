package com.example.voicerecognitionaiengine;
import android.Manifest;
import android.annotation.SuppressLint;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.RandomAccessFile;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import android.util.Log;
import java.util.ArrayList;
import java.util.List;
import java.io.ByteArrayOutputStream;
import android.os.Handler;

public class MainActivity extends AppCompatActivity {
    private static final int REQUEST_RECORD_AUDIO_PERMISSION = 200;
    private boolean permissionToRecordAccepted = false;
    private String[] permissions = {Manifest.permission.RECORD_AUDIO};
	private static final int SAMPLE_RATE = 16000; // 16 kHz
	private static final int AUDIO_SOURCE = android.media.MediaRecorder.AudioSource.MIC;
	private static final int CHANNEL_CONFIG = android.media.AudioFormat.CHANNEL_IN_MONO;
	private static final int AUDIO_FORMAT = android.media.AudioFormat.ENCODING_PCM_16BIT;
	List<String> labels;
    private TextView predictionTextView;
	private TextView TextView1;
	private Button recordButton;
    private TFLiteInterpreter tfliteInterpreter;
    private boolean isRecording = false;
 	private android.media.AudioRecord audioRecord;
 	private String filePath;
	private static final String TAG = "AudioRecordApp";
	private File wavFile;
	
	@Override
    protected void onCreate(Bundle savedInstanceState) { super.onCreate(savedInstanceState);
		filePath = getExternalCacheDir().getAbsolutePath() + "/recorded.wav";
		Log.i("AudioRecorder", filePath);

		setContentView(R.layout.activity_main);
        predictionTextView = findViewById(R.id.predictionTextView);
		TextView1=findViewById(R.id.TextView1);
		recordButton = findViewById(R.id.recordButton);
		// Request audio recording permission
        ActivityCompat.requestPermissions(this, permissions, REQUEST_RECORD_AUDIO_PERMISSION);
		// Initialize TensorFlow Lite interpreter
        try {
            tfliteInterpreter = new TFLiteInterpreter(getAssets(),"voice_recognition_model.tflite");
            labels = loadLabels( "labels.txt");
        
        } catch (IOException e) {
            e.printStackTrace();
        }
		recordButton.setOnClickListener(v -> {
			if (!isRecording) {
				TextView1.setText("Analyzing...");
				startRecording();
			} else {
				stopRecording();
			}
			TextView1.setText("Results");
		});
 
	}
	private void startRecording() {
		if (isRecording) {
			Toast.makeText(this, "Recording is already in progress", Toast.LENGTH_SHORT).show();
			return;
		}
		
		int bufferSize = android.media.AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT);
		if (ActivityCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
			// TODO: Consider calling
			//    ActivityCompat#requestPermissions
			// here to request the missing permissions, and then overriding
			//   public void onRequestPermissionsResult(int requestCode, String[] permissions,
			//                                          int[] grantResults)
			// to handle the case where the user grants the permission. See the documentation
			// for ActivityCompat#requestPermissions for more details.
			return;
		}
		audioRecord = new android.media.AudioRecord(AUDIO_SOURCE, SAMPLE_RATE, CHANNEL_CONFIG, AUDIO_FORMAT, bufferSize);
		
		// Create WAV file
		try {
			File dir = getExternalFilesDir(null);
			wavFile = new File(dir, "audio_recording.wav");
			if (wavFile.exists()) wavFile.delete();
			wavFile.createNewFile();
		} catch (IOException e) {
			Log.e(TAG, "Error creating WAV file", e);
			return;
		}
		
		audioRecord.startRecording();
		isRecording = true;
		
		// Record for 1 second
		new Handler().postDelayed(() -> {
			stopRecording();
			Toast.makeText(this, "Recording saved", Toast.LENGTH_SHORT).show();
			// Pass the file to TensorFlow Lite model here
			try {
				startAudioRecognitionProcess(getExternalFilesDir(null)+"/audio_recording.wav");
			} catch (IOException e) {
				throw new RuntimeException(e);
			}
		}, 1000);
		
		new Thread(() -> writeAudioDataToFile(bufferSize)).start();
	}
	private void writeAudioDataToFile(int bufferSize) {
		byte[] audioData = new byte[bufferSize];
		int totalAudioLen = 0;
		int totalDataLen = 0;
		int channels = CHANNEL_CONFIG == android.media.AudioFormat.CHANNEL_IN_MONO ? 1 : 2;
		int bitsPerSample = AUDIO_FORMAT == android.media.AudioFormat.ENCODING_PCM_16BIT ? 16 : 8;
		
		try (FileOutputStream fos = new FileOutputStream(wavFile)) {
			// Write placeholder WAV header
			writeWavHeader(fos, SAMPLE_RATE, channels, bitsPerSample);
			
			while (isRecording) {
				int read = audioRecord.read(audioData, 0, audioData.length);
				if (read > 0) {
					fos.write(audioData, 0, read);
					totalAudioLen += read;
				}
			}
			
			// Update WAV header with correct sizes
			totalDataLen = totalAudioLen + 36;
			updateWavHeader(fos, totalDataLen, totalAudioLen, SAMPLE_RATE, channels, bitsPerSample);
		} catch (IOException e) {
			Log.e(TAG, "Error writing audio data", e);
		}
	}
	private void stopRecording() {
		if (audioRecord != null) {
			isRecording = false;
			audioRecord.stop();
			audioRecord.release();
			audioRecord = null;
		}
 	}
	private void writeWavHeader(FileOutputStream out, int sampleRate, int channels, int bitsPerSample) throws IOException {
		byte[] header = new byte[44];
		long byteRate = sampleRate * channels * bitsPerSample / 8;
		
		// ChunkID "RIFF"
		header[0] = 'R';
		header[1] = 'I';
		header[2] = 'F';
		header[3] = 'F';
		
		// ChunkSize (placeholder, to be updated later)
		header[4] = 0;
		header[5] = 0;
		header[6] = 0;
		header[7] = 0;
		
		// Format "WAVE"
		header[8] = 'W';
		header[9] = 'A';
		header[10] = 'V';
		header[11] = 'E';
		
		// Subchunk1ID "fmt "
		header[12] = 'f';
		header[13] = 'm';
		header[14] = 't';
		header[15] = ' ';
		
		// Subchunk1Size (PCM format: 16)
		header[16] = 16;
		header[17] = 0;
		header[18] = 0;
		header[19] = 0;
		
		// AudioFormat (PCM: 1)
		header[20] = 1;
		header[21] = 0;
		
		// NumChannels
		header[22] = (byte) channels;
		header[23] = 0;
		
		// SampleRate
		header[24] = (byte) (sampleRate & 0xff);
		header[25] = (byte) ((sampleRate >> 8) & 0xff);
		header[26] = (byte) ((sampleRate >> 16) & 0xff);
		header[27] = (byte) ((sampleRate >> 24) & 0xff);
		
		// ByteRate
		header[28] = (byte) (byteRate & 0xff);
		header[29] = (byte) ((byteRate >> 8) & 0xff);
		header[30] = (byte) ((byteRate >> 16) & 0xff);
		header[31] = (byte) ((byteRate >> 24) & 0xff);
		
		// BlockAlign
		header[32] = (byte) (channels * bitsPerSample / 8);
		header[33] = 0;
		
		// BitsPerSample
		header[34] = (byte) bitsPerSample;
		header[35] = 0;
		
		// Subchunk2ID "data"
		header[36] = 'd';
		header[37] = 'a';
		header[38] = 't';
		header[39] = 'a';
		
		// Subchunk2Size (placeholder, to be updated later)
		header[40] = 0;
		header[41] = 0;
		header[42] = 0;
		header[43] = 0;
		
		out.write(header, 0, 44);
	}
	private void updateWavHeader(FileOutputStream out, int totalDataLen, int totalAudioLen, int sampleRate, int channels, int bitsPerSample) throws IOException {
		try (RandomAccessFile raf = new RandomAccessFile(wavFile, "rw")) {
			raf.seek(4);
			raf.write(intToByteArray(totalDataLen), 0, 4);
			raf.seek(40);
			raf.write(intToByteArray(totalAudioLen), 0, 4);
		}
	}
	
	private byte[] intToByteArray(int value) {
		return new byte[]{
				(byte) (value & 0xFF),
				(byte) ((value >> 8) & 0xFF),
				(byte) ((value >> 16) & 0xFF),
				(byte) ((value >> 24) & 0xFF)
		};
	}
	
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) { super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case REQUEST_RECORD_AUDIO_PERMISSION:
                permissionToRecordAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                break;
        }
        if (!permissionToRecordAccepted ) {
            Toast.makeText(this, "Permission to record audio denied", Toast.LENGTH_SHORT).show();
                    finish();
        }
    }
    @SuppressLint("MissingPermission")
    private void startAudioRecognitionProcess(String fileName) throws IOException {
	    float[] inputBuffer = null; // Your 16000 audio samples as floats
	       try {
		    inputBuffer = loadWavFile(fileName);
			runInferenceOnAudio(inputBuffer);
	       }
	    catch (IOException e) {
		    throw new RuntimeException(e);
	    }
	}
	
	private float[] loadWavFile(String externalFilePath) throws IOException {
		// Open the file from external storage
		File file = new File(externalFilePath);
		if (!file.exists()) {
			throw new FileNotFoundException("File not found: " + externalFilePath);
		}
		
		InputStream inputStream = new FileInputStream(file);
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		byte[] buffer = new byte[1024];
		int read;
		
		// Read the file into a byte array
		while ((read = inputStream.read(buffer)) != -1) {
			bos.write(buffer, 0, read);
		}
		inputStream.close(); // Always close the stream after use
		
		byte[] wavBytes = bos.toByteArray();
		
		// WAV header is typically 44 bytes for a standard PCM WAV
		if (wavBytes.length <= 44) {
			throw new IOException("Invalid WAV file: " + externalFilePath);
		}
		
		// Extract raw PCM data after the header
		ByteBuffer bb = ByteBuffer.wrap(wavBytes, 44, wavBytes.length - 44);
		bb.order(ByteOrder.LITTLE_ENDIAN);
		
		float[] floatBuffer = new float[16000]; // Adjust based on your expected buffer size
		for (int i = 0; i < floatBuffer.length && bb.remaining() >= 2; i++) {
			short s = bb.getShort(); // Read 16-bit PCM samples
			floatBuffer[i] = s / 32768.0f; // Convert to float (-1.0 to 1.0)
		}
		
		return floatBuffer;
	}
    private List<String> loadLabels(  String fileName) throws IOException {
        List<String> labels = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(getAssets().open(fileName)))) {
            String line;
            while ((line = reader.readLine()) != null) {
                labels.add(line.trim());
            }
        }
        return labels;
    }
 
    private void runInferenceOnAudio(  float[] inputBuffer){
	    float[][] input = new float[1][16000];
        input[0] = inputBuffer;
	
		// The model outputs [1, number_of_labels]
        float[][] output = new float[1][labels.size()];
		
		// Run inference
        tfliteInterpreter.runInference(input, output);
		
		// output[0] now contains the logits for each class.
		
	    // Convert logits to probabilities via softmax if needed:
        float[] probabilities = softmax(output[0]);
     
        // Get the most likely predicted label
        int minIndex = 0;
        int maxIndex = 0;
        for (int i = 1; i < probabilities.length; i++) {
             if (probabilities[i] > probabilities[maxIndex]) {
                maxIndex = i;
            }
        }
        for (int i = 0; i < probabilities.length; i++) {
            //if all elements values is < 0.45 then non-classified msg
            if (probabilities[i] < 0.45) {
                minIndex+=1;
            }
            }
        if(minIndex < probabilities.length)
        {
            String predictedLabel = labels.get(maxIndex);
	        predictionTextView.setTextColor(Color.GREEN);
	        predictionTextView.setText(predictedLabel);
         }
        else{
	        predictionTextView.setTextColor(Color.RED);
	        predictionTextView.setText("Non classified/trained data" );
        }
    }
	
	public float[] softmax(float[] logits) {
		float max = Float.NEGATIVE_INFINITY;
		for (float logit : logits) {
			if (logit > max) max = logit;
		}
		float sum = 0.0f;
		for (int i = 0; i < logits.length; i++) {
			logits[i] = (float)Math.exp(logits[i] - max);
			sum += logits[i];
		}
		for (int i = 0; i < logits.length; i++) {
			logits[i] /= sum;
		}
		return logits;
	}
	
	@Override
    protected void onDestroy() {
        super.onDestroy();
        if (tfliteInterpreter != null) {
            tfliteInterpreter.close();
        }
//        executorService.shutdown();
    }
}
