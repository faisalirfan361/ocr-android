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
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.highgui.Highgui;
import org.opencv.imgproc.Imgproc;
import org.opencv.utils.Converters;

import android.app.Activity;
import android.content.Intent;
import android.content.res.AssetManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
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
	protected String _path;
	protected boolean _taken;
	private Bitmap bitmap;
	protected static final String PHOTO_TAKEN = "photo_taken";

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
				//GZIPInputStream gin = new GZIPInputStream(in);
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
				//gin.close();
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
			startCameraActivity();
		}
	}   

	// Simple android photo capture:
	// http://labs.makemachine.net/2010/03/simple-android-photo-capture/

	protected void startCameraActivity() {
		File file = new File(_path);
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

		try {
			ExifInterface exif = new ExifInterface(_path);
			int exifOrientation = exif.getAttributeInt(
					ExifInterface.TAG_ORIENTATION,
					ExifInterface.ORIENTATION_NORMAL);

			Log.v(TAG, "Orient: " + exifOrientation);

			int rotate = 0;

			switch (exifOrientation) {
			case ExifInterface.ORIENTATION_ROTATE_90:
				rotate = 90;
				break;
			case ExifInterface.ORIENTATION_ROTATE_180:
				rotate = 180;
				break;
			case ExifInterface.ORIENTATION_ROTATE_270:
				rotate = 270;
				break;
			}

			Log.v(TAG, "Rotation: " + rotate);

			if (rotate != 0) {

				// Getting width & height of the given image.
				int w = bitmap.getWidth();
				int h = bitmap.getHeight();

				// Setting pre rotate
				Matrix mtx = new Matrix();
				mtx.preRotate(rotate);

				// Rotating Bitmap
				bitmap = Bitmap.createBitmap(bitmap, 0, 0, w, h, mtx, false);
			}

			// Convert to ARGB_8888, required by tess
			bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);

		} catch (IOException e) {
			Log.e(TAG, "Couldn't correct orientation: " + e.toString());
		}

		_image.setImageBitmap( bitmap );

		Log.v(TAG, "Before baseApi");


		//findEdges(bitmap);
		//edgeDetection();

		Log.i(TAG, "Trying to load OpenCV library");
		if (!OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this, mOpenCVCallBack))
		{
			Log.e(TAG, "Cannot connect to OpenCV Manager");
		}
		else{ Log.i(TAG, "opencv successfull"); 

		System.out.println(java.lang.Runtime.getRuntime().maxMemory()); 

		}


		/*TessBaseAPI baseApi = new TessBaseAPI();
		baseApi.setDebug(true);
		baseApi.init(DATA_PATH, lang);
		baseApi.setImage(bitmap);

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
		}*/
	}

	//http://www.phonesdevelopers.com/1707520/
	private void edgeDetection() {

		double scale = 0.1;

		/*Bitmap bm1=BitmapFactory.decodeFile(_path);
		imageview.setImageBitmap(bm1);*/
		Mat img = Highgui.imread(_path,0);

		Size dsize = new Size(img.width()*scale,img.height()*scale);
		Mat img2 = new Mat(dsize,CvType.CV_8SC1);
		Mat img3 = new Mat();
		img.convertTo(img2, CvType.CV_8SC1);
		Imgproc.Canny(img, img3, 123, 250);

		boolean flag=Highgui.imwrite(DATA_PATH+"/new.jpg", img3);
		if(flag)
		{
			File f = new File(DATA_PATH+"/new.jpg");
			if(f.exists())
			{
				Bitmap bm=BitmapFactory.decodeFile(DATA_PATH+"/new.jpg");
				_image.setImageBitmap(bm);
			}
		}//end if
		else{
			Toast.makeText(SimpleAndroidOCRActivity.this, "===========?============??", 3).show();
		}

	}
	//Implement anyone of these methods: edgeDetection() , findEdges(Bitmap bmp)
	//http://stackoverflow.com/questions/23969897/how-to-detect-edeges-android-opencv
	private void findEdges(Bitmap bmp) {

		//Bitmap bmp; //input image
		Mat srcMat = new Mat ( bmp.getHeight(), bmp.getWidth(), CvType.CV_8UC3);
		Bitmap myBitmap32 = bmp.copy(Bitmap.Config.ARGB_8888, true);
		Utils.bitmapToMat(myBitmap32, srcMat);

		//Now perform canny edge detection on converted Mat, before that convert to gray,
		Mat gray = new Mat(srcMat.size(), CvType.CV_8UC1);
		Imgproc.cvtColor(srcMat, gray, Imgproc.COLOR_RGB2GRAY,4); 

		//Perform canny and convert to 4 channel
		Mat edge = new Mat();
		Mat dst = new Mat();
		Imgproc.Canny(gray, edge, 80, 90);
		Imgproc.cvtColor(edge, dst, Imgproc.COLOR_GRAY2RGBA,4);

		//Finally convert to bitmap
		Bitmap resultBitmap = Bitmap.createBitmap(dst.cols(), dst.rows(),Bitmap.Config.ARGB_8888);           
		Utils.matToBitmap(dst, resultBitmap);

		_image.setImageBitmap(resultBitmap);
	}

	private void findHoughLines(){

		Mat mYuv = new Mat();
		Mat mRgba = new Mat();

		int height = bitmap.getHeight();

		int width = bitmap.getWidth();

		Mat thresholdImage = new Mat(height+ height/ 2, width, CvType.CV_8UC1);
		//mYuv.put(0, 0, data);
		Imgproc.cvtColor(mYuv, mRgba, Imgproc.COLOR_YUV420sp2RGB, 4);
		Imgproc.cvtColor(mRgba, thresholdImage, Imgproc.COLOR_RGB2GRAY, 4);
		Imgproc.Canny(thresholdImage, thresholdImage, 80, 100);
		Mat lines = new Mat();
		int threshold = 50;
		int minLineSize = 20;
		int lineGap = 20;

		try{

			Imgproc.HoughLinesP(thresholdImage, lines, 1, Math.PI/180, threshold, minLineSize, lineGap);

		}catch(Exception e){
			/*
			 * CvException [org.opencv.core.CvException: cv::Exception: /home/reports/ci/slave_desktop/50-SDK/opencv/modules/core/src/array.cpp:2482: error: (-206) Unrecognized or unsupported array type in function CvMat* cvGetMat(const CvArr*, CvMat*, int*, int)
			 */
			System.out.print(e.getMessage());
		}

		for (int x = 0; x < lines.cols(); x++) 
		{
			double[] vec = lines.get(0, x);
			double  x1 = vec[0], 
					y1 = vec[1],
					x2 = vec[2],
					y2 = vec[3];
			Point start = new Point(x1, y1);
			Point end = new Point(x2, y2);
			Core.line(mRgba, start, end, new Scalar(255,0,0), 3);
		}


		//Imgproc.cvtColor(thresholdImage, mRgba, Imgproc.COLOR_GRAY2BGRA, 4);

		Bitmap bmp = Bitmap.createBitmap(mRgba.cols(), mRgba.rows(), Bitmap.Config.ARGB_8888);

		//Bitmap bmp = Bitmap.createBitmap(getFrameWidth(), getFrameHeight(), Bitmap.Config.ARGB_8888);

		Utils.matToBitmap(mRgba, bmp);

		_image.setImageBitmap(bmp);
		//return bmp;

		bmp.recycle();
	}

	private BaseLoaderCallback mOpenCVCallBack = new BaseLoaderCallback(this) {
		@Override
		public void onManagerConnected(int status) {
			switch (status) {
			case LoaderCallbackInterface.SUCCESS:
			{
				Log.i(TAG, "OpenCV loaded successfully");
				//edgeDetection();
				//findEdges(bitmap);
				//findHoughLines();
				//rectangles(bitmap);
				
				
				
				EdgeDetection edges = new EdgeDetection(getApplicationContext(), _path);
				Uri inputImageUri = edges.AutoRotation();
				Bitmap bmps = edges.readBitmap(inputImageUri);
				_image.setImageURI(inputImageUri);
						
			} break;


			default:
			{
				super.onManagerConnected(status);
			} break;
			}
		}
	};

	private void rectangles(Bitmap bmp){

		//Bitmap bmp; //input image
		Mat srcMat = new Mat ( bmp.getHeight(), bmp.getWidth(), CvType.CV_8UC3);
		Bitmap myBitmap32 = bmp.copy(Bitmap.Config.ARGB_8888, true);
		Utils.bitmapToMat(myBitmap32, srcMat);

		//Now perform canny edge detection on converted Mat, before that convert to gray,
		Mat gray = new Mat(srcMat.size(), CvType.CV_8UC1);
		Imgproc.cvtColor(srcMat, gray, Imgproc.COLOR_RGB2GRAY,4); 

		//Perform canny and convert to 4 channel
		Mat edge = new Mat();
		Mat dst = new Mat();
		Imgproc.Canny(gray, edge, 80, 90);
		Imgproc.cvtColor(edge, dst, Imgproc.COLOR_GRAY2RGBA,4);

		//convert the image to black and white does (8 bit)
		Imgproc.Canny(srcMat, srcMat, 50, 50);

		//apply gaussian blur to smoothen lines of dots
		Imgproc.GaussianBlur(srcMat, srcMat, new  org.opencv.core.Size(5, 5), 5);

		//find the contours
		List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
		Imgproc.findContours(srcMat, contours, new Mat(), Imgproc.RETR_LIST, Imgproc.CHAIN_APPROX_SIMPLE);

		double maxArea = -1;
		int maxAreaIdx = -1;
		Log.d("size",Integer.toString(contours.size()));
		MatOfPoint temp_contour = contours.get(0); //the largest is at the index 0 for starting point
		MatOfPoint2f approxCurve = new MatOfPoint2f();
		MatOfPoint largest_contour = contours.get(0);
		//largest_contour.ge
		List<MatOfPoint> largest_contours = new ArrayList<MatOfPoint>();
		//Imgproc.drawContours(imgSource,contours, -1, new Scalar(0, 255, 0), 1);

		for (int idx = 0; idx < contours.size(); idx++) {
			temp_contour = contours.get(idx);
			double contourarea = Imgproc.contourArea(temp_contour);
			//compare this contour to the previous largest contour found
			if (contourarea > maxArea) {
				//check if this contour is a square
				MatOfPoint2f new_mat = new MatOfPoint2f( temp_contour.toArray() );
				int contourSize = (int)temp_contour.total();
				MatOfPoint2f approxCurve_temp = new MatOfPoint2f();
				Imgproc.approxPolyDP(new_mat, approxCurve_temp, contourSize*0.05, true);
				if (approxCurve_temp.total() == 4) {
					maxArea = contourarea;
					maxAreaIdx = idx;
					approxCurve=approxCurve_temp;
					largest_contour = temp_contour;
				}
			}
		}

		Imgproc.cvtColor(srcMat, srcMat, Imgproc.COLOR_BayerBG2RGB);
		srcMat =Highgui.imread(Environment.getExternalStorageDirectory().
				getAbsolutePath() +"/scan/p/1.jpg");
		double[] temp_double;
		temp_double = approxCurve.get(0,0);       
		Point p1 = new Point(temp_double[0], temp_double[1]);
		//Core.circle(imgSource,p1,55,new Scalar(0,0,255));
		//Imgproc.warpAffine(sourceImage, dummy, rotImage,sourceImage.size());
		temp_double = approxCurve.get(1,0);       
		Point p2 = new Point(temp_double[0], temp_double[1]);
		// Core.circle(imgSource,p2,150,new Scalar(255,255,255));
		temp_double = approxCurve.get(2,0);       
		Point p3 = new Point(temp_double[0], temp_double[1]);
		//Core.circle(imgSource,p3,200,new Scalar(255,0,0));
		temp_double = approxCurve.get(3,0);       
		Point p4 = new Point(temp_double[0], temp_double[1]);
		// Core.circle(imgSource,p4,100,new Scalar(0,0,255));
		List<Point> source = new ArrayList<Point>();
		source.add(p1);
		source.add(p2);
		source.add(p3);
		source.add(p4);
		Mat startM = Converters.vector_Point2f_to_Mat(source);
		Mat result=warp(srcMat,startM);

		Utils.matToBitmap(result, bmp);

		_image.setImageBitmap(bmp);
	}

	public Mat warp(Mat inputMat,Mat startM) {

		int resultWidth = 1000;
		int resultHeight = 1000;

		Mat outputMat = new Mat(resultWidth, resultHeight, CvType.CV_8UC4);

		Point ocvPOut1 = new Point(0, 0);
		Point ocvPOut2 = new Point(0, resultHeight);
		Point ocvPOut3 = new Point(resultWidth, resultHeight);
		Point ocvPOut4 = new Point(resultWidth, 0);
		List<Point> dest = new ArrayList<Point>();
		dest.add(ocvPOut1);
		dest.add(ocvPOut2);
		dest.add(ocvPOut3);
		dest.add(ocvPOut4);
		Mat endM = Converters.vector_Point2f_to_Mat(dest);      

		Mat perspectiveTransform = Imgproc.getPerspectiveTransform(startM, endM);

		Imgproc.warpPerspective(inputMat, 
				outputMat,
				perspectiveTransform,
				new Size(resultWidth, resultHeight), 
				Imgproc.INTER_CUBIC);

		return outputMat;
	}
}
