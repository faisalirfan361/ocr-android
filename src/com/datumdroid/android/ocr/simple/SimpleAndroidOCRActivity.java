package com.datumdroid.android.ocr.simple;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.android.Utils;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

import com.googlecode.tesseract.android.TessBaseAPI;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.Toast;

public class SimpleAndroidOCRActivity extends Activity {

	public static final String PACKAGE_NAME = "com.datumdroid.android.ocr.simple";
	public static final String DATA_PATH = Environment.getExternalStorageDirectory().toString() + "/SimpleAndroidOCR/";

	// You should have the trained data file in assets folder
	// You can get them at:
	// http://code.google.com/p/tesseract-ocr/downloads/list
	public static final String lang = "eng";
	private static final String TAG = "SimpleAndroidOCR.java";
	protected Button _button;
	protected ImageView _image;
	protected EditText _field;
	protected static String _path;
	protected boolean _taken;
	private static Bitmap bitmap;   
	protected static final String PHOTO_TAKEN = "photo_taken";
	private static List<MatOfPoint> contours;
	private static Mat imgSource;
	private static Mat outputMat;
	//public static ArrayList emailList = new ArrayList();

	@Override
	public void onCreate(Bundle savedInstanceState) {

		String[] paths = new String[] { DATA_PATH, DATA_PATH + "tessdata/" };

		for (String path : paths) {

			File dir = new File(path);
			if (!dir.exists()) {
				if (!dir.mkdirs()) {
					Log.v(TAG, "ERROR: Creation of directory " + path + " on sdcard failed");
					return;
				} else {
					Log.v(TAG, "Created directory " + path + " on sdcard");
				}
			}
		}

		// lang.traineddata file with the app (in assets folder)
		// You can get them at:
		// http://code.google.com/p/tesseract-ocr/downloads/list
		// This area needs work and optimization
		if (!(new File(DATA_PATH + "tessdata/" + lang + ".traineddata")).exists()) {
			try {

				AssetManager assetManager = getAssets();
				InputStream in = assetManager.open("tessdata/" + lang + ".traineddata");
				OutputStream out = new FileOutputStream(DATA_PATH
						+ "tessdata/" + lang + ".traineddata");

				// Transfer bytes from in to out
				byte[] buf = new byte[1024];
				int len;
				//while ((lenf = gin.read(buff)) > 0) {
				while ((len = in.read(buf)) > 0) {
					out.write(buf, 0, len);
				}
				in.close();
				out.close();

				Log.v(TAG, "Copied " + lang + " traineddata");
			} catch (IOException e) {
				Log.e(TAG, "Was unable to copy " + lang + " traineddata " + e.toString());
			}
		}

		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		_image = (ImageView) findViewById(R.id.image);
		_field = (EditText) findViewById(R.id.field);
		_button = (Button) findViewById(R.id.button);
		_button.setOnClickListener(new ButtonClickHandler());
		_path = DATA_PATH + "/ocr.jpg";
	}

	public class ButtonClickHandler implements View.OnClickListener {
		public void onClick(View view) {
			Log.v(TAG, "Starting Camera app");
			if(_field.getText().toString().trim()!=null) {

				_field.setText("");
			}
			startCameraActivity();
		}
	}   

	// Simple android photo capture:
	// http://labs.makemachine.net/2010/03/simple-android-photo-capture/
	protected void startCameraActivity() {

		File file = new File(DATA_PATH + "/ocr.jpg");
		Uri outputFileUri = Uri.fromFile(file);

		final Intent intent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
		intent.putExtra(MediaStore.EXTRA_OUTPUT, outputFileUri);

		startActivityForResult(intent, 0);
	}

	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

		Log.i(TAG, "resultCode: " + resultCode);

		if (resultCode == -1) {
		
			onPhotoTaken();
		} else {
			Log.v(TAG, "User cancelled");
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putBoolean(SimpleAndroidOCRActivity.PHOTO_TAKEN, _taken);
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		Log.i(TAG, "onRestoreInstanceState()");
		if (savedInstanceState.getBoolean(SimpleAndroidOCRActivity.PHOTO_TAKEN)) {
			onPhotoTaken();
		}
	}

	protected void onPhotoTaken() {

		_taken = true;
		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inSampleSize = 4;

		bitmap = BitmapFactory.decodeFile(_path, options);		

		/*Matrix matrix = new Matrix();
		matrix.preRotate(90);	
		Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap , 0, 0, bitmap .getWidth(), bitmap .getHeight(), matrix, true);*/
		_image.setImageBitmap(bitmap);

		// Convert to ARGB_8888, required by tess
		bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

		Log.i(TAG, "Trying to load OpenCV library");
		if (!OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mOpenCVCallBack))
		{
			Log.e(TAG, "Cannot connect to OpenCV Manager");
		}
		else
		{
			Log.i(TAG, "opencv successfull"); 
			System.out.println(java.lang.Runtime.getRuntime().maxMemory()); 
		}
	}

	private BaseLoaderCallback mOpenCVCallBack = new BaseLoaderCallback(this) {

		@Override
		public void onManagerConnected(int status) {
			switch (status) {

			case LoaderCallbackInterface.SUCCESS:
			{
				Log.i(TAG, "OpenCV loaded successfully");

				try{

					correctPerspective(); //set the correct perspective of picture

					Mat result_final = locateText(outputMat); //identify text and draw bounding box (rectangle)
					Utils.matToBitmap(result_final, bitmap);//DATA_PATH + "/corrected.jpg"

					/*Matrix matrix = new Matrix();
					matrix.postRotate(90);
					Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap , 0, 0, bitmap .getWidth(), bitmap .getHeight(), matrix, true);
					_image.setImageBitmap(rotatedBitmap);*/
					//Bitmap rotatedBitmap = rotateImage();
										
					Uri uri = Uri.parse(DATA_PATH+"/locate_text.jpg");
					Intent photoIntent = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
					photoIntent.putExtra(MediaStore.EXTRA_OUTPUT, uri);
					
		            String[] orientationColumn = {MediaStore.Images.Media.ORIENTATION};
		            Cursor cur = managedQuery(uri, orientationColumn, null, null, null);
		            int orientation = -1;
		            if (cur != null && cur.moveToFirst()) {
		                orientation = cur.getInt(cur.getColumnIndex(orientationColumn[0]));
		            }  
		            Matrix matrix = new Matrix();
		            matrix.postRotate(orientation);
									
		            bitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
		            
					_image.setImageBitmap(bitmap);
					readText(bitmap);

				}catch(Exception e){

				}		
			} break;

			default:
			{
				super.onManagerConnected(status);
			} break;
			} 
		}
	};

	public Bitmap toGrayscale(Bitmap bmpOriginal)
	{        
		int width, height;
		height = bmpOriginal.getHeight();
		width = bmpOriginal.getWidth();    

		Bitmap bmpGrayscale = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
		Canvas c = new Canvas(bmpGrayscale);
		Paint paint = new Paint();
		ColorMatrix cm = new ColorMatrix();
		cm.setSaturation(0);
		ColorMatrixColorFilter f = new ColorMatrixColorFilter(cm);
		paint.setColorFilter(f);
		c.drawBitmap(bmpOriginal, 0, 0, paint);
		return bmpGrayscale;
	}


	public void correctPerspective() {

		try{

			//http://iswwwup.com/t/8a8246d90603/auto-perspective-correction-using-opencv-and-java.html
			imgSource = Highgui.imread(_path, Highgui.CV_LOAD_IMAGE_UNCHANGED);

			// convert the image to black and white does (8 bit)
			Imgproc.Canny(imgSource.clone(), imgSource, 50, 50);

			// apply gaussian blur to smoothen lines of dots
			Imgproc.GaussianBlur(imgSource, imgSource, new org.opencv.core.Size(5, 5), 5);

			// find the contours
			contours = new ArrayList<MatOfPoint>();

			Imgproc.findContours(imgSource, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);
			double maxArea = -1;
			MatOfPoint temp_contour = contours.get(0); // the largest contour is at the index 0 for starting point
			MatOfPoint2f approxCurve = new MatOfPoint2f();

			for (int idx = 0; idx < contours.size(); idx++) { 

				temp_contour = contours.get(idx);
				double contourarea = Imgproc.contourArea(temp_contour);
				// compare this contour to the previous largest contour found
				if (contourarea > maxArea) {
					// check if this contour is a square
					MatOfPoint2f new_mat = new MatOfPoint2f(temp_contour.toArray());
					int contourSize = (int) temp_contour.total();
					MatOfPoint2f approxCurve_temp = new MatOfPoint2f();
					Imgproc.approxPolyDP(new_mat, approxCurve_temp, contourSize * 0.05, true);
					if (approxCurve_temp.total() == 4) {
						maxArea = contourarea;
						approxCurve = approxCurve_temp;
					}
				}
			}

			Imgproc.cvtColor(imgSource, imgSource, Imgproc.COLOR_BayerBG2RGB);
			Mat sourceImage = Highgui.imread(_path, Highgui.CV_LOAD_IMAGE_UNCHANGED);

			double[] temp_double;
			temp_double = approxCurve.get(0, 0);
			Point p1 = new Point(temp_double[0], temp_double[1]);
			//Core.circle(imgSource,p1,55,new Scalar(0,0,255));
			temp_double = approxCurve.get(1, 0);
			Point p2 = new Point(temp_double[0], temp_double[1]);
			//Core.circle(imgSource,p2,150,new Scalar(255,255,255));
			temp_double = approxCurve.get(2, 0);
			Point p3 = new Point(temp_double[0], temp_double[1]);
			//Core.circle(imgSource,p3,200,new Scalar(255,0,0));
			temp_double = approxCurve.get(3, 0);
			Point p4 = new Point(temp_double[0], temp_double[1]);
			//Core.circle(imgSource,p4,100,new Scalar(0,0,255));
			List<Point> source = new ArrayList<Point>();
			source.add(p1);
			source.add(p2);
			source.add(p3);
			source.add(p4);
			Mat startM = Converters.vector_Point2f_to_Mat(source);		

			Mat result = warp(sourceImage, startM);

			Highgui.imwrite(DATA_PATH+"/correct_perspective.jpg", result);

		}catch(Exception e){

		}
	}
	public static Mat warp(Mat inputMat, Mat startM) {

		try{

			int resultWidth = bitmap.getWidth();
			int resultHeight = bitmap.getHeight();

			Point ocvPOut4 = new Point(0, 0);
			Point ocvPOut1 = new Point(0, resultHeight);
			Point ocvPOut2 = new Point(resultWidth, resultHeight);
			Point ocvPOut3 = new Point(resultWidth, 0);

			if (inputMat.height() > inputMat.width()) {

				ocvPOut3 = new Point(0, 0);
				ocvPOut4 = new Point(0, resultHeight);
				ocvPOut1 = new Point(resultWidth, resultHeight);
				ocvPOut2 = new Point(resultWidth, 0);
			}

			outputMat = new Mat(resultWidth, resultHeight, CvType.CV_8UC4); 

			List<Point> dest = new ArrayList<Point>();
			dest.add(ocvPOut1);
			dest.add(ocvPOut2);
			dest.add(ocvPOut3);
			dest.add(ocvPOut4);

			Mat endM = Converters.vector_Point2f_to_Mat(dest);		
			Mat perspectiveTransform = Imgproc.getPerspectiveTransform(startM, endM);

			Imgproc.warpPerspective(inputMat, outputMat, perspectiveTransform, new Size(resultWidth, resultHeight), Imgproc.INTER_CUBIC);

			/*Utils.matToBitmap(outputMat, bitmap);
			_image.setImageBitmap(bitmap);*/

			/*Utils.matToBitmap(outputMat, bitmap);
			Matrix matrix = new Matrix();
			matrix.preRotate(90);	
			Bitmap rotatedBitmap = Bitmap.createBitmap(bitmap , 0, 0, bitmap .getWidth(), bitmap .getHeight(), matrix, true);

			Utils.bitmapToMat(rotatedBitmap, outputMat);*/
			return outputMat;

		}catch(Exception e){

			return outputMat;
		}
	}

	//Locate Text
	//http://stackoverflow.com/questions/26814069/how-to-set-region-of-interest-in-opencv-java
	public Mat locateText(Mat perspective){

		try{

			Mat img_grayROI =  Highgui.imread(_path, Highgui.CV_LOAD_IMAGE_GRAYSCALE);

			Utils.matToBitmap(img_grayROI, bitmap);
			_image.setImageBitmap(bitmap);
			//Imgproc.GaussianBlur(img_grayROI, img_grayROI,  new Size(3, 3), 5);

			Imgproc.threshold(img_grayROI, img_grayROI, -1, 255, Imgproc.THRESH_BINARY_INV+Imgproc.THRESH_OTSU);	

			Imgproc.dilate(img_grayROI, img_grayROI, Imgproc.getStructuringElement(Imgproc.MORPH_RECT, new Size(5, 7)));

			Mat heirarchy= new Mat();
			Point shift=new Point(150,0);

			List<MatOfPoint> contours = new ArrayList<MatOfPoint>(); 
			Imgproc.findContours(img_grayROI, contours, heirarchy, Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_NONE);
			double[] cont_area =new double[contours.size()]; 

			for(int i=0; i< contours.size();i++){

				if (Imgproc.contourArea(contours.get(i)) > 35 ){  

					Rect rect = Imgproc.boundingRect(contours.get(i));
					cont_area[i]=Imgproc.contourArea(contours.get(i));

					if (rect.height > 25){

						Core.rectangle(perspective, new Point(rect.x,rect.y), new Point(rect.x+rect.width,rect.y+rect.height),new Scalar(0,0,255),2);
						System.out.println(rect.x +"-"+ rect.y +"-"+ rect.height+"-"+rect.width);
						Highgui.imwrite(DATA_PATH+"/locate_text.jpg",perspective);
					}
				}
			}
			return perspective;
		}catch(Exception e){

			return perspective;
		}
	} 

	private void readText(Bitmap bm) {


		Toast.makeText(getApplicationContext(), "Extracting Text:", Toast.LENGTH_LONG).show();

		/*Matrix matrix = new Matrix();

		matrix.preRotate(90);

		Bitmap rotatedBitmap = Bitmap.createBitmap(bm , 0, 0, bm .getWidth(), bm .getHeight(), matrix, true);

		_image.setImageBitmap(rotatedBitmap);*/

		//Bitmap btmp = rotateImage(bm);

		TessBaseAPI baseApi = new TessBaseAPI();
		baseApi.setDebug(true);
		baseApi.init(DATA_PATH, lang);

		baseApi.setVariable(TessBaseAPI.VAR_CHAR_WHITELIST, "!@#_=-0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ");
		baseApi.setPageSegMode(TessBaseAPI.OEM_TESSERACT_CUBE_COMBINED);
		baseApi.setImage(bm);
		String recognizedText = baseApi.getUTF8Text();
		baseApi.end();

		// You now have the text in recognizedText var, you can do anything with it.
		// We will display a stripped out trimmed alpha-numeric version of it (if lang is eng)
		// so that garbage doesn't make it to the display.

		Log.v(TAG, "OCRED TEXT: " + recognizedText);
		if ( lang.equalsIgnoreCase("eng") ) {
			recognizedText = recognizedText.replaceAll("[^a-zA-Z0-9]+", " ");
		}

		recognizedText = recognizedText.trim();
		if ( recognizedText.length() != 0 ) {

			_field.setText(_field.getText().toString().length() == 0 ? recognizedText : _field.getText() + " " + recognizedText);
			_field.setSelection(_field.getText().toString().length());
			Toast.makeText(getApplicationContext(), "Text: "+recognizedText, Toast.LENGTH_LONG).show();

			//getEmail(recognizedText);
			//isValidEmailAddress(recognizedText);
		}	
	}

	public static Bitmap RotateBitmap(Bitmap source, float angle)
	{
		Matrix matrix = new Matrix();
		matrix.postRotate(angle);
		return Bitmap.createBitmap(source, 0, 0, source.getWidth(), source.getHeight(), matrix, true);
	}

	/*private Bitmap rotateImage()
	{
		Bitmap resultBitmap = null;

		try
		{
			ExifInterface exifInterface = new ExifInterface(DATA_PATH+"/locate.jpg");
			int orientation = exifInterface.getAttributeInt(ExifInterface.TAG_ORIENTATION, 1);

			Matrix matrix = new Matrix();

			if (orientation == ExifInterface.ORIENTATION_ROTATE_90)
			{
				matrix.postRotate(ExifInterface.ORIENTATION_ROTATE_90);
			}
			else if (orientation == ExifInterface.ORIENTATION_ROTATE_180)
			{
				matrix.postRotate(ExifInterface.ORIENTATION_ROTATE_180);
			}
			else if (orientation == ExifInterface.ORIENTATION_ROTATE_270)
			{
				matrix.postRotate(ExifInterface.ORIENTATION_ROTATE_270);
			}

			// Rotate the bitmap
			resultBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
		}
		catch (Exception exception)
		{
			Log.d("rotateImage(Bitmap bitmap):","Could not rotate the image");
		}
		return resultBitmap;
	}*/
}