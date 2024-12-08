package com.example.voicerecognitionaiengine;
 
import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import org.tensorflow.lite.Interpreter;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
	public class TFLiteInterpreter
	{
		private Interpreter interpreter;
		public TFLiteInterpreter(AssetManager assetManager, String modelPath) throws IOException {
			interpreter = new Interpreter(loadModelFile(assetManager, modelPath));
			
		}
		private MappedByteBuffer loadModelFile(AssetManager assets, String modelPath) throws IOException {
			AssetFileDescriptor fileDescriptor = assets.openFd(modelPath);
			FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
			FileChannel fileChannel = inputStream.getChannel();
			long startOffset = fileDescriptor.getStartOffset();
			long declaredLength = fileDescriptor.getDeclaredLength();
			return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
		}
        public float[][] runInference(float[][] object1, float[][] object2) {
			// Define output shape based on your model's output
			float[][] output = new float[1][8]; // Assuming 8 labels
			interpreter.run(object1,object2 );
			return object2;
		}
 
		public void close() {
			if (interpreter != null) {
				interpreter.close();
			}
		}
	}