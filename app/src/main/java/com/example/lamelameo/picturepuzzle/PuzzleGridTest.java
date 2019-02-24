package com.example.lamelameo.picturepuzzle;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Handler;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.support.v7.widget.GridLayout;
import android.util.Log;
import android.view.*;
import android.widget.*;

import java.io.*;
import java.util.*;

public class PuzzleGridTest extends AppCompatActivity implements PauseMenu.OnFragmentInteractionListener {

    private ArrayList<Drawable> bitmaps = new ArrayList<>();
    private String TAG = "PuzzleGridTest";
    private ArrayList<ArrayList<ImageView>> cellRows, cellCols;
    private ArrayList<ImageView> gridCells;
    private int emptyCellIndex;
    private VelocityTracker mVelocityTracker = null;
    private float xDown, yDown;
    private int numRows;
    private int timerCount;
    private int puzzleNum;
    private int numMoves;
    private ArrayList<String> savedDataList = new ArrayList<>();
    private TextView moveCounter;
    private boolean gamePaused = false;
    private Runnable timerRunnable;
    private Handler timerHandler;
    private PauseMenu pauseMenu;

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_puzzle_grid_test);

        // ~~~ set up the puzzle grid ~~~

        // first get relevant chosen settings from main activity
        final GridLayout puzzleGrid = findViewById(R.id.gridLayout);
        final int numCols = getIntent().getIntExtra("numColumns", 4);
        numRows = numCols;
        int gridSize = puzzleGrid.getLayoutParams().width;
        puzzleNum = getIntent().getIntExtra("puzzleNum", 0);
        String photoPath = getIntent().getStringExtra("photoPath");

        // create puzzle piece bitmaps using the given image and add to bitmaps list
        if (photoPath == null) {  // use one of the default images
            int gridBitmap = getIntent().getIntExtra("drawableId", R.drawable.dfdfdefaultgrid);
            Bitmap bmp = BitmapFactory.decodeResource(getResources(), gridBitmap);
            int imageSize = bmp.getWidth();
            createBitmapGrid(bmp, numCols, numCols, imageSize);
        } else {  // have taken a photo - so use it for the image
            // scales the photo to the puzzle grid and creates the grid (ends up cropping off the bottom)
            final Bitmap bmp2 = scalePhoto(gridSize, photoPath);
            createBitmapGrid(bmp2, numCols, numCols, gridSize);
        }

        // initialise grid settings
        puzzleGrid.setColumnCount(numCols);
        puzzleGrid.setRowCount(numCols);
        emptyCellIndex = numCols*numCols-1;
        int gridWidth = puzzleGrid.getLayoutParams().width;  //TODO: could change this based on screen?

        // initialise lists to hold grid objects
        gridCells = new ArrayList<>();
        cellRows = new ArrayList<>();
        cellCols =  new ArrayList<>();
        for (int x=0; x<numCols; x++) {
            cellRows.add(new ArrayList<ImageView>());
            cellCols.add(new ArrayList<ImageView>());
        }

        // create randomised grid list - contains indexes in random order which can be used to assign bitmaps to cells
        ArrayList<Integer> randomisedGrid = randomiseGrid(numCols);

        // add cells to grid and set their now randomised images
        //TODO: allow for m x n sized grids?
        for (int x=0; x<numCols; x++) {
            for (int y=0; y<numCols; y++) {
                final int index = x*numCols + y;
                ImageView gridCell = new ImageView(this);
                // set cell size based on size of grid
                int size = gridWidth/numCols;
                gridCell.setLayoutParams(new ViewGroup.LayoutParams(size, size));
                // add cell to grid
                puzzleGrid.addView(gridCell, index);
                //add cell to appropriate row/col lists
                gridCells.add(gridCell);
                cellRows.get(x).add(gridCell);
                cellCols.get(y).add(gridCell);

                // setting images and tags for cells
                if (index == bitmaps.size() - 1) {  // leave last cell with no image
                    int[] cellTag = {index, index};
                    gridCell.setTag(cellTag);
                    // set all other cells with a randomised image excluding the last cells image as it must be empty
                } else {  //
                    int rngBitmapIndex = randomisedGrid.get(index);
                    // set the cells starting image
                    gridCell.setImageDrawable(bitmaps.get(rngBitmapIndex));
                    //set cell tags corresponding to the cell position and set image for tracking/identification purposes
                    int[] cellTag = {index, rngBitmapIndex};
                    gridCell.setTag(cellTag);
                }

                // set click/touch listeners for cells
                //TODO: maybe use custom imageviews for cells as this warning appears about not overriding performclick..
                gridCell.setOnTouchListener(swipeListener);
                gridCell.setOnClickListener(cellClickListener);

            }
        }

        // show the saved best time and move data for the given puzzle
        TextView bestTimeView = findViewById(R.id.bestTimeView);
        int[] bestData = puzzleBestData();
        if (bestData[0] != -1) {
            int secs = bestData[0] % 60;
            int mins = bestData[0] / 60;
            int bestMoves = bestData[1];
            bestTimeView.setText(String.format(Locale.getDefault(), "Best Time: %02d:%02d\nBest Moves: %d",
                                 mins, secs, bestMoves));
        }

        // initialise game timer and its related handler/runnables
        moveCounter = findViewById(R.id.moveCounter);
        final TextView timer = findViewById(R.id.gameTimer);
        timerCount = 0;
        timer.setText("0");
        // handler allows runnables to be called at delayed time intervals, and to be removed from queue at will
        timerHandler = new Handler();
        // Create runnable task (calls code in new thread) which increments a counter used as the timers text
        timerRunnable = new Runnable() {
            @Override
            public void run() {
                timerCount += 1;
                int seconds = timerCount % 60;
                int minutes = timerCount / 60;  // rounds down the decimal if we use int
                timer.setText(String.format(Locale.getDefault(), "%d:%02d", minutes, seconds));
            }
        };

        // initialise pause button and pause menu fragment
        pauseMenu = PauseMenu.newInstance();
        ImageButton pauseButton = findViewById(R.id.pauseButton);
        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                // pause the timer and open pause menu
                pauseTimer();
                pauseFragment();
            }
        });

    }

    @Override
    public void onClickNewPuzzle() {
        finish();
    }

    @Override
    public void onClickResume() {
        pauseFragment();
        startTimer();
    }

    @Override
    public void onBackPressed() {
        // handles back button clicks considering two cases: pause fragment open or closed
        super.onBackPressed();

        if (gamePaused) {  // fragment is present - remove it, start timer
            pauseFragment();
            startTimer();
            Log.i(TAG, "container not empty ");
        } else {  // fragment is not open - go back to main activity
            Log.i(TAG, "container empty");
            finish();
        }
    }

    /**
     * Handles adding or removing the pause menu fragment from he game activity. If the game is paused when called,
     * remove fragment and make its container invisible and unclickable. If game is unpaused do the opposite actions.
     */
    private void pauseFragment() {

        LinearLayout pauseContainer = findViewById(R.id.pauseContainer);
        FragmentTransaction fragmentTrans = getSupportFragmentManager().beginTransaction();
        if (gamePaused) {
            fragmentTrans.remove(pauseMenu);
            pauseContainer.setVisibility(View.INVISIBLE);
            pauseContainer.setClickable(false);
        } else {
            fragmentTrans.add(R.id.pauseContainer, pauseMenu);
            pauseContainer.setVisibility(View.VISIBLE);
            pauseContainer.setClickable(true);
        }
        gamePaused = !gamePaused;
        fragmentTrans.addToBackStack(null);  //TODO: needed?
        fragmentTrans.commit();
    }

    /**
     * A method to post delayed runnable tasks which increment the timer by 1 every 1000ms.
     */
    private void startTimer() {
        // call the runnable every 1 second using a handler - times up to 1 hour
        for (int x=0; x<360; x++) {
            timerHandler.postDelayed(timerRunnable, x*1000);
        }
    }

    /**
     * Method to stop the game timer by removing the runnable calls from the stack which are responsible for timing.
     */
    private void pauseTimer() {
        timerHandler.removeCallbacks(timerRunnable);
    }

    @Override
    protected void onPause() {
        super.onPause();
        pauseTimer();
        Log.i(TAG, "onPause: ");
    }

    @Override
    protected void onResume() {
        super.onResume();
        startTimer();
        Log.i(TAG, "onResume: ");
    }

    int getEmptyCellIndex() {
        return emptyCellIndex;
    }

    int getNumRows()  {
        return numRows;
    }

    ArrayList<ImageView> getCellRow(int index) {
        return cellRows.get(index);
    }

    ArrayList<ImageView> getCellCol(int index) {
        return cellCols.get(index);
    }

    /** Called anytime the game activity is created - on the first occasion it creates a file in the apps directory
     * to save the following game data: lowest amount of time and moves taken to complete each puzzle
     * If the file has already been created, then search the file for the saved data for the current puzzle and return it
     * (if any) else return placeholder values to signify no data is available
     * @return an array[2] containing an int for both the saved time and moves data
     */
    private int[] puzzleBestData() {

        FileInputStream saveTimesFile = null;
        int[] savedData = {-1, -1};
        StringBuilder stringBuilder = new StringBuilder();
        String[] puzzleStrings = {"defaultgrid: ", "carpet: ", "cat: ", "clock: ", "crab: ",
                "darklights: ", "nendou: ", "razer: ", "saiki: ", "mms: "};

        Log.i(TAG, "puzzleNum: "+puzzleNum);
        try {  // if file already created, read file and get the relevant time, append lines to an array for easy access
            saveTimesFile = openFileInput("gametimes");
            BufferedReader reader = new BufferedReader(new InputStreamReader(saveTimesFile));
            String line;
            int currentLine = 0;
            if (puzzleNum == -1) {
                //TODO: add support for photos taken by the app...change puzzleNum for these, and add lines in file as needed
                return savedData;
            }
            // loop through lines till we get the puzzle we are looking for and get the saved data from that line
            while ((line = reader.readLine()) != null) {
                Log.i(TAG, "lines: " + line);
                savedDataList.add(line);
                if (currentLine == puzzleNum) {
                    int timeStartIndex = line.indexOf(":") + 2;
                    int timeEndIndex = line.indexOf(",");  // indexOf will give -1 if not found ie no data for the puzzle
                    Log.i(TAG, "timeStartIndex: "+timeStartIndex);
                    Log.i(TAG, "timeEndIndex: "+timeEndIndex);
                    if (timeEndIndex != -1) {  // if there is saved data then get it
                        savedData[0] = Integer.valueOf(line.substring(timeStartIndex, timeEndIndex));
                        savedData[1] = Integer.valueOf(line.substring(timeEndIndex+1));
                        Log.i(TAG, "savedData: "+savedData[0]+","+savedData[1]);
                    }
                }
                currentLine += 1;
            }

        } catch (Exception readException) {
            readException.printStackTrace();
            // create the file if there is none already
            if (readException instanceof FileNotFoundException) {
                Log.i(TAG, "made time file ");
                for (String element : puzzleStrings) {
                    stringBuilder.append(element).append("\n");
                    savedDataList.add(element);
                }
                // string builder contains a single string separated by newlines with blank save data
                String fileContents = stringBuilder.toString();
                // create file containing the string builder string
                FileOutputStream outputStream = null;
                try {
                    outputStream = openFileOutput("gametimes", Context.MODE_PRIVATE);
                    outputStream.write(fileContents.getBytes());
                } catch (Exception writeError) {
                    Log.i(TAG, "write exception: "+writeError);
                } finally {  //TODO: need finally block to close outputstream?
                    if (outputStream != null) {
                        try {
                            outputStream.close();
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    }
                }
            }

        } finally {  // close the file
            try {
                if (saveTimesFile != null) {
                    saveTimesFile.close();
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        return savedData;
    }

    /** Called when a puzzle is successfully solved, compares the time and moves taken to complete the puzzle
     *  to the saved data for the lowest value of each of these taken to complete the same puzzle (if any)
     *  updates the saved data file if either (or both) value(s) for the completed puzzle is lower than the saved data
     * @param gameData array[2] containing an int for the amount of time (seconds) and moves to complete the puzzle
     */
    private void saveGameData(int[] gameData) {
        //TODO: support for different sized grid times

        String gameTime = Integer.toString(gameData[0]);
        String gameMoves = Integer.toString(gameData[1]);
        StringBuilder stringBuilder = new StringBuilder();
        Log.i(TAG, "puzzleNum: "+puzzleNum);

        if (puzzleNum == -1) {
            //TODO: add support for photos taken by the app...change puzzleNum for these, and add lines in file as needed
            return;
        }

        String savedData = savedDataList.get(puzzleNum);
        String newData = "";
        int timeStartIndex = savedData.indexOf(":") + 2;
        int timeEndIndex = savedData.indexOf(",");
        String puzzleIdentifier = savedData.substring(0, timeStartIndex);
        if (timeEndIndex == -1) {  // if there is no saved data then add the game data
            newData = puzzleIdentifier + gameTime + "," + gameMoves + "\n";
        } else {  // there is saved data, so check if game time/moves and update if either are lower
            String timeString = savedData.substring(timeStartIndex, timeEndIndex);
            String moveString = savedData.substring(timeEndIndex+1);
            // time and moves are lower than saved values, so update
            if (gameData[0] < Integer.valueOf(timeString) && gameData[1] < Integer.valueOf(moveString)) {
                newData = puzzleIdentifier + gameTime + "," + gameMoves + "\n";
            }  // update time only
            if (gameData[0] < Integer.valueOf(timeString) && gameData[1] > Integer.valueOf(moveString)) {
                newData = puzzleIdentifier + gameTime + "," + moveString + "\n";
            }  // update moves only
            if (gameData[0] > Integer.valueOf(timeString) && gameData[1] < Integer.valueOf(moveString)) {
                newData = puzzleIdentifier + timeString + "," + gameMoves + "\n";
            }
        }
        // if newdata is changed, then we have to update the data file
        if (!newData.equals("")) {
            // update the relevant item in the data list, with the new string
            savedDataList.remove(puzzleNum);
            savedDataList.add(puzzleNum, newData);
            // use string builder to concatenate all strings
            for (String element : savedDataList) {
                stringBuilder.append(element);
            }
            String fileContents = stringBuilder.toString();
            // overwrite the old file to contain the updated data
            FileOutputStream outputStream = null;
            try {
                outputStream = openFileOutput("gametimes", Context.MODE_PRIVATE);
                outputStream.write(fileContents.getBytes());
            } catch (Exception writeError) {
                Log.i(TAG, "write exception: "+writeError);
            } finally {  //TODO: need finally block to close outputstream?
                if (outputStream != null) {
                    try {
                        outputStream.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    /** create a list containing cell position indexes (0-14) in a random order which can be used to arrange bitmaps
     in the grid in this randomised order.
     * This order of cells checked for its solvability and returns the list if it is, or repeats the process if not
     * @param numCols number of columns in the puzzle grid
     */
    private ArrayList<Integer> randomiseGrid(int numCols) {
        // creates a randomised grid that is guaranteed to be solvable - empty cell is always bottom right
        Random random = new Random();
        ArrayList<Integer> randomisedGrid = new ArrayList<>();
        ArrayList<Integer> posPool = new ArrayList<>();
        int gridSize = numCols*numCols;
        // list of ascending values from 0 - size of grid used for tracking values tested for inversions
        ArrayList<Integer> unTestedValues = new ArrayList<>();

        while (true) {  // create randomised grid in while loop and only break if
            // initialise variables for start of each loop
            int bound = gridSize-1;  // bounds for random generator...between 0 (inclusive) and number (exclusive)
            randomisedGrid.clear();
            for (int x=0; x<gridSize-1; x++) {  // pool for random indexes to be drawn from - exclude last cell index
                posPool.add(x);
            }

            // randomise grid and create list with outcome
            for (int x=0; x<gridSize; x++) {
                unTestedValues.add(x);
                if (x == gridSize-1) {  // add last index to last in list to ensure it is empty
                    randomisedGrid.add(gridSize-1);
                } else {
                    int rngIndex = random.nextInt(bound);  // gets a randomised number within the pools bounds
                    int rngBmpIndex = posPool.get(rngIndex); // get the bitmap index from the pool using the randomised number
                    posPool.remove((Integer) rngBmpIndex);  // remove used number from the pool - use Integer else it takes as Arrayindex
                    bound -= 1;  // lower the bounds by 1 to match the new pool size so the next cycle can function properly
                    randomisedGrid.add(rngBmpIndex);  // add the randomised bmp index to the gridList
                }
            }

            // n=odd -> inversions: even = solvable
            // n=even -> empty cell on even row (from bottom: 1,2,3++ = 1 for bottom right) + inversions: odd = solvable
            //        -> empty cell on odd row + inversions: even = solvable
            // inversion: position pairs (a,b) where (list) index a < index b and (value) a > b have to check all

            int inversions = 0;
            for (int index=0; index<gridSize-1; index++) {  // test all grid cells for pairs with higher index cells
                int currentNum = randomisedGrid.get(index);
                for (int x=index+1; x<gridSize; x++) {  // find all pairs with higher index than current selected cell
                    int pairNum = randomisedGrid.get(x);  // get the next highest index cell
                    if (currentNum > pairNum) {  // add inversion if paired cell value is less than current cell value
                        inversions += 1;
                    }

                }
            }

            //TODO: alternate method to count inversions, faster or slower? above is n(n-1)/2 for n size grid (sum nat nums)
            // logn for search, n for remove, but n decreases each loop

//            // index of the current value being tested in the ordered values = how many lesser values have a greater
//            // index than it and therefore how many inversions there are for this value - if we remove tested values
//            // as we go, the order remains but possible inversions are removed
//            int inversions = 0;
//            for (int index=0; index<gridSize-1; index++) {  // test all grid cells for pairs with higher index cells
//                int currentNum = randomisedGrid.get(index);  // O(1) to access in array
//                int numInvs = unTestedValues.indexOf(currentNum);  // O(logn) to find value in sorted array
//                inversions += numInvs;
//                // remove current num before next loop, use its index so no search is needed
//                unTestedValues.remove(numInvs);  // O(n) to remove item at current index in sorted array
//            }
//            unTestedValues.remove(0);  // remove the last remaining value as we dont loop anymore

            Log.i(TAG, "randomiseGrid: inversions "+inversions);
            // if randomised grid is sovlable then break the while loop and return that grid - else next loop creates new grid
            if (inversions%2 == 0) {  // empty cell always on bottom right so both odd and even size grids need even inversions
                break;
            }
        }
        return randomisedGrid;
    }

    /** convert density independent pixels to pixels using the devices pixel density
     * @param dp amount to be converted from dp to px
     */
    int dpToPx(float dp) {
        float density = getResources().getDisplayMetrics().density;
        // rounds up/down around 0.5
        long pixels = Math.round(dp * density);
        return (int) pixels;
    }

    /** Scale an image from a file path to a specific views size, and return as a Bitmap
     * Intended to be used for photos taken with a camera intent so images are by default in landscape
     * Therefore images are also rotated 90 degrees
     * @param viewSize size of the (pixels) view that the image is intended to be placed into
     * @param photopath file path of the image to be scaled
     */
    private Bitmap scalePhoto(int viewSize, String photopath) {
        // scale image previews to fit the allocated View to save app memory
        BitmapFactory.Options bmOptions = new BitmapFactory.Options();
        bmOptions.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(photopath, bmOptions);
        int photoW = bmOptions.outWidth;
        int photoH = bmOptions.outHeight;
        int scaleFactor = Math.min(photoW/viewSize, photoH/viewSize);
        bmOptions.inJustDecodeBounds = false;
        bmOptions.inSampleSize = scaleFactor;

        // rotate image to correct orientation - default is landscape
        Matrix matrix = new Matrix();
        matrix.postRotate(90);
        Bitmap bitmap = BitmapFactory.decodeFile(photopath, bmOptions);
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), matrix, true);
    }


    /** create the grid of smaller cell bitmaps using the chosen image and grid size and add them to the bitmaps list
     * @param bmp bitmap image to be used to create grid of images for the puzzle
     * @param rows number of rows to split the grid into
     * @param columns number of columns to split the grid into
     * @param imageSize size of the bitmap image (in pixels)
     */
    private void createBitmapGrid(Bitmap bmp, int rows, int columns, int imageSize) {
        // determine cell size in pixels from the image size and set amount of rows/cols
        int cellSize = imageSize/rows;
        // for each row loop 4 times creating a new cropped image from original bitmap and add to adapter dataset
        for(int x=0; x<columns; x++) {
            // for each row, increment y value to start the bitmap at
            float ypos = x*cellSize;
            for(int y=0; y<rows; y++) {
                // loop through 4 positions in row incrementing the x value to start bitmap at
                float xpos = y*cellSize;
                Bitmap gridImage = Bitmap.createBitmap(bmp, (int)xpos, (int)ypos, (int)cellSize, (int)cellSize);
                // converted to drawable for use of setImageDrawable to easily swap cell images
                Drawable drawable = new BitmapDrawable(getResources(), gridImage);
                bitmaps.add(drawable);
            }
        }
    }

    /** determine if grid is solved by iterating through all cells to check if the cell position matches the set image
     cell tag[0] gives position, tag[1] is the image index, if they are the same then image is in correct cell
     displays a Toast message if the grid is solved */
    private boolean gridCorrect() {
        // TODO: left as boolean as can use the value for stopping the game and adding the last cell as animation
        // get grid size and iterate through all cells
        int numCells = gridCells.size();
        for (int x=0; x<numCells; x++) {
            // get the tags of each cell in the grid
            int[] cellTag = (int[])gridCells.get(x).getTag();
            Log.i(TAG, "gridCorrectCell: "+x+" tag: "+cellTag[1]);
            if (cellTag[0] != cellTag[1]) {
                return false;
            }
        }
        // emoticons: 😃 😁 😄 😎 😊 ☻ 👍 🖒 ☜ ☞
        Toast gameWin = Toast.makeText(getApplicationContext(), "Correct \uD83D\uDE0E", Toast.LENGTH_LONG);
        gameWin.show();
        return true;
    }

    /** Handle single clicks on any cell other than the empty cell - these are filtered out in the touchListener
     * checks if the clicked cell is a direct neighbour of the empty cell
     * if so then calls MoveCells to handle movement of images/tags and checking if grid is solved */
    private View.OnClickListener cellClickListener = new View.OnClickListener() {
        @Override
        public void onClick(View v) {
            ImageView cellImage = (ImageView)v;
            int[] cellTag = (int[])cellImage.getTag();
            int cellIndex = cellTag[0];
            int lastCell = numRows*numRows-1;
            // check if cell is in same row or column as empty cell
            int emptyCellRow = (int)Math.floor(emptyCellIndex/(float)numRows);
            int emptyCellCol = emptyCellIndex - emptyCellRow*numRows;
            int cellRow = (int)Math.floor(cellIndex/(float)numRows);
            int cellCol = cellIndex - cellRow*numRows;
            // determine distance and direction from the empty cell, opposite to the swipe (move) direction
            int cellsRowDiff = cellCol - emptyCellCol;  // left = -1, right = 1
            int cellsColDiff = cellRow - emptyCellRow;  // up = -1, down = 1
            // if cell is in same group then call movecells to make only one swap in the appropriate direction
            if (cellRow == emptyCellRow && cellsRowDiff*cellsRowDiff == 1) {  // if cell is left/right of empty
                MoveCells(1, emptyCellCol, lastCell, cellIndex, cellsRowDiff, cellRows.get(cellRow));
            }
            if (cellCol == emptyCellCol && cellsColDiff*cellsColDiff == 1) {  // if cell is up/down of empty
                MoveCells(1, emptyCellRow, lastCell, cellIndex, cellsColDiff, cellCols.get(cellCol));
            }
        }
    };

    /** called if a swipe in the valid direction occurs on a cell in same row/column of the empty cell or if a click
     registers on any cell other than the empty one
     * swaps cell images and image tags for all cells between that clicked (included) and empty cell
     * lastly calls gridcorrect to check if grid is solved */
    void MoveCells(int groupMoves, int emptyGroupIndex, int lastCell, int gridIndex, int iterateSign,
                   ArrayList<ImageView> group) {
        // get the empty cell and the adjacent cell in a given group (row/col) then get the tags and image to be swapped
        for (int x = 0; x < groupMoves; x++) {  // incrementally swap cells from empty -> touched, in this order
            // adjacent cell to be swapped with empty has an index either +/- 1 from empty cells index in the group
            // increment - up/left, decrement - down/right, swapIndex is always the emptyCell index as we swap then loop
            int swapIndex = emptyGroupIndex + (iterateSign*x);
            ImageView swapCell = group.get(swapIndex + iterateSign);
            ImageView emptyCell = group.get(swapIndex);
            Drawable image = swapCell.getDrawable();
            int[] swapTag = (int[])swapCell.getTag();
            int[] emptyTag = (int[])emptyCell.getTag();
            // set empty cells new image and tag
            emptyCell.setImageDrawable(image);
            emptyTag[1] = swapTag[1];
            // set touched cells new image and tag
            swapCell.setImageDrawable(null);
            swapTag[1] = lastCell;  // use gridsize - 1 rather than empty cell tag as it was just changed
            // update empty cell tracker
            emptyCellIndex = gridIndex;
        }
        // track amount of moves taken and update move counter to display this
        numMoves += groupMoves;
        moveCounter.setText(String.valueOf(numMoves));
        // check if grid is solved, if so then check if the game data was lower than the saved data (if any)
        int[] gameData = {timerCount, numMoves};
        if (gridCorrect()) {
            //TODO: stop game timer (how to?)...open menu on top of UI, make rest unresponsive - fragment?
            saveGameData(gameData);
        }
    }

    /** Determines if a swiped cell is within the same row or column as the empty cell and if the swipe was
        in the direction of the empty cell - if so then calls MoveCells to process the valid swipe */
    void SwipeCell(View view, int gridCols, int direction) {
        // obtaining cell image, the row and column lists they are part of and their index in those lists
        int[] cellTag = (int[])view.getTag();
        int gridIndex = cellTag[0];
        int cellRow = (int)Math.floor(gridIndex/(float)gridCols);
        int cellCol = gridIndex - cellRow*gridCols;
        ArrayList<ImageView> row = cellRows.get(cellRow);
        ArrayList<ImageView> col = cellCols.get(cellCol);

        int lastCell = gridCols*gridCols - 1;
        int emptyCellRow = (int)Math.floor(emptyCellIndex/(float)gridCols);
        int emptyCellCol = emptyCellIndex - emptyCellRow*gridCols;
        int numRowMoves = Math.abs(emptyCellCol - cellCol);
        int numColMoves = Math.abs(emptyCellRow - cellRow);

        // check if empty and touched cells are in same row/col and if correct swipe direction
        switch (direction) {
            case(1):  // right swipe - empty row index > touched row index
                if (emptyCellRow == cellRow && emptyCellCol > cellCol) {  // cell columns give the index in row
                    MoveCells(numRowMoves, emptyCellCol, lastCell, gridIndex,-1, row);
                }
                break;
            case(2):  // left swipe - empty row index < touched row index
                if (emptyCellRow == cellRow && emptyCellCol < cellCol) {
                    MoveCells(numRowMoves, emptyCellCol, lastCell, gridIndex,1, row);
                }
                break;
            case(3):  // down swipe - empty col index > touched col index
                if (emptyCellCol == cellCol && emptyCellRow > cellRow) {  // cell rows give the index in column
                    MoveCells(numColMoves, emptyCellRow, lastCell, gridIndex,-1, col);
                }
                break;
            case(4):  // up swipe - empty col index < touched col index
                if (emptyCellCol == cellCol && emptyCellRow < cellRow) {
                    MoveCells(numColMoves, emptyCellRow, lastCell, gridIndex,1, col);
                }
                break;
        }
    }

    /** Handle simple touch events as either a swipe (up/down/left/right) or a click - multi touch is not supported.
     * Swipe direction is determined by distance travelled in the x/y planes between touch/release and its release velocity.
     * Distance and velocity must be greater than @DISTANCE_THRESHOLD and @VELOCITY_THRESHOLD, respectively, to be valid.
     * An event that doesn't fit any of the set criteria is considered a click and handed to the onClick listener. */
    private View.OnTouchListener swipeListener = new View.OnTouchListener() {
        @Override
        public boolean onTouch(View v, MotionEvent event) {
            int[] tag = (int[])v.getTag();
            // consume touch with no action taken if empty cell is touched
            if (tag[0] == emptyCellIndex) {
                Log.i(TAG, "emptyCellTouched");
                return true;
            }

            // TODO: multi finger touch.. think ive handled it but keeping jsut in case..
            // get pointer id which identifies the touch...can handle multi touch events
            int action = event.getActionMasked();
            int index = event.getActionIndex();
            int pointerId = event.getPointerId(index);
            Log.i(TAG, "pointer ID: "+pointerId);

            // if the pointer id isnt 0, a touch is currently being processed - ignore this new one to avoid crashes
            if (pointerId != 0) {
                Log.i(TAG, "multi touch detected");
                return true;
            }

            final int DISTANCE_THRESHOLD = dpToPx(11);  // ~1/3 the cell size
            final int VELOCITY_THRESHOLD = 200;  // TODO: BALANCE VALUE
            int gridSize = (int)Math.sqrt(gridCells.size());

            float xVelocity, xCancel, xDiff, yVelocity, yCancel, yDiff;
            switch (action) {
                case MotionEvent.ACTION_DOWN:
                    Log.i(TAG, "onDown");
                    if (mVelocityTracker == null) {
                        mVelocityTracker = VelocityTracker.obtain();
                    } else {
                        mVelocityTracker.clear();
                    }
                    mVelocityTracker.addMovement(event);
                    xDown = event.getRawX();
                    yDown = event.getRawY();
                    break;
                case MotionEvent.ACTION_MOVE:
                    Log.i(TAG, "onMove: ");
                    mVelocityTracker.addMovement(event);
                    mVelocityTracker.computeCurrentVelocity(1000);
//                    xVelocity = mVelocityTracker.getXVelocity(pointerId);
//                    yVelocity = mVelocityTracker.getYVelocity(pointerId);
//                    float xMove = event.getRawX();
//                    float yMove = event.getRawY();
                    break;
                case MotionEvent.ACTION_UP:
                    Log.i(TAG, "onTouch: ");
                case MotionEvent.ACTION_OUTSIDE:
                    Log.i(TAG, "onOutside: ");
                case MotionEvent.ACTION_CANCEL:
                    Log.i(TAG, "onCancel");
                    mVelocityTracker.addMovement(event);
                    mVelocityTracker.computeCurrentVelocity(1000);
                    xCancel = event.getRawX();
                    yCancel = event.getRawY();
                    xDiff = xCancel - xDown;
                    yDiff = yCancel - yDown;
                    xVelocity = mVelocityTracker.getXVelocity(pointerId);
                    yVelocity = mVelocityTracker.getYVelocity(pointerId);
                    Log.i(TAG, "xDown: "+xDown+" xCancel: "+xCancel+" yDown: "+yDown+" yCancel: "+yCancel);
                    Log.i(TAG, "diffX: "+xDiff+" diffY: "+yDiff+" velX: "+xVelocity+" velY: "+yVelocity);

                    if (Math.abs(xDiff) > Math.abs(yDiff)) {  // potential horizontal swipe - check distance and velocity
                        if (Math.abs(xDiff) > DISTANCE_THRESHOLD && Math.abs(xVelocity) > VELOCITY_THRESHOLD) {
                            if (xDiff > 0) {  // right swipe
                                Log.i(TAG, "rightSwipe ");
                                SwipeCell(v, gridSize, 1);
                            } else {  // left swipe
                                Log.i(TAG, "leftSwipe");
                                SwipeCell(v, gridSize, 2);
                            }
                        } else {
                            v.performClick();
                        }
                    } else {
                        if (Math.abs(xDiff) < Math.abs(yDiff)) {  // potential vertical swipe
                            if (Math.abs(yDiff) > DISTANCE_THRESHOLD && Math.abs(yVelocity) > VELOCITY_THRESHOLD) {
                                if (yDiff > 0) {  // down swipe
                                    Log.i(TAG, "downSwipe");
                                    SwipeCell(v, gridSize, 3);
                                } else {  // up swipe
                                    Log.i(TAG, "upSwipe");
                                    SwipeCell(v, gridSize, 4);
                                }
                            } else {
                                v.performClick();
                            }
                        } else {  // if swipe isnt found to have happened by any of the set criteria
                            v.performClick();
                        }
                    }
                    // reset velocity tracker
                    mVelocityTracker.recycle();
                    mVelocityTracker = null;
                    break;
            }
            return true;
        }
    };
}
