/*
 * Copyright (C) 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.example.squash;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

import com.google.android.gms.appstate.AppStateClient;
import com.google.android.gms.appstate.OnStateLoadedListener;
import com.google.android.gms.plus.PlusShare;
import com.google.example.games.basegameutils.BaseGameActivity;
import com.google.example.squash.replay.ReplayView;

import java.io.UnsupportedEncodingException;

public class SquashActivity extends BaseGameActivity
        implements OnStateLoadedListener {

    // If this is not 0, that app will show a challenge (for lesson 6!)
    public static int challengeScore = 0;

    public static int REQUEST_ACHIEVEMENTS = 1001;
    public static int REQUEST_LEADERBOARD = 1002;

    public static int LAST_SCORE_STATE = 0;

    public SquashActivity() {
        super(CLIENT_GAMES | CLIENT_APPSTATE | CLIENT_PLUS);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_leaderboard:
                startActivityForResult(getGamesClient().getLeaderboardIntent(
                        getResources().getString(R.string.leaderboard_bounces)),
                        REQUEST_LEADERBOARD);
                return true;
            case R.id.menu_reset:
                return true;
            case R.id.menu_achievements:
                if (isSignedIn()) {
                    startActivityForResult(getGamesClient().getAchievementsIntent(),
                            REQUEST_ACHIEVEMENTS);
                }
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_squash);

        // Need this to pass to anonymous class below.
        final SquashActivity bind = this;

        // This sets up the click listener.
        findViewById(R.id.sign_in_button).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // start the asynchronous sign in flow
                        beginUserInitiatedSignIn();
                    }
                });

        findViewById(R.id.sign_out_button).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        // This button isn't visible.
                        signOut();
                        setSigninButtonState();
                    }
                });

        findViewById(R.id.replay_button).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        SquashView sv = (SquashView) bind
                                .findViewById(R.id.squashView);
                        if (sv.mState == SquashView.STATE_RUNNING) {
                            return;
                        }
                        ReplayView rv = (ReplayView) bind
                                .findViewById(R.id.replayView);
                        rv.setReplaying(!rv.mIsReplaying);
                    }
                });

        findViewById(R.id.share_button).setOnClickListener(
                new View.OnClickListener() {
                    @Override
                    public void onClick(View view) {
                        int lastScore = ((SquashView) bind
                                .findViewById(R.id.squashView)).mScore;

                        // Note that we use "bind" here to get the
                        // current activity.
                        PlusShare.Builder builder = new PlusShare.Builder(
                                bind, getPlusClient());
                        // Set call-to-action metadata, which includes
                        // the score to beat!
                        builder.addCallToAction("PLAY",
                                Uri.parse("http://goo.gl/Sa7tR"), "/"
                                + lastScore);

                        // Set the content url (for desktop use).
                        builder.setContentUrl(Uri
                                .parse("http://goo.gl/Sa7tR"));

                        // Set the target deep-link ID (for mobile use).
                        builder.setContentDeepLinkId("/", null, null, null);

                        //Set the share text.
                        builder.setText("Can you beat my last score of "
                                + lastScore + "?");

                        startActivityForResult(builder.getIntent(), 0);
                    }
                });

    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.squash, menu);

        return true;
    }

    @Override
    public void onResume() {
        super.onResume();
        ((SquashView) findViewById(R.id.squashView)).setAnimating(true);
    }

    @Override
    public void onStop() {
        super.onStop();
        ((SquashView) findViewById(R.id.squashView)).setAnimating(false);
    }

    // Called whenever the Squash game starts.
    public void onGameStart(SquashView v) {
        if (isSignedIn()) {
            getGamesClient().unlockAchievement(
                    getResources().getString(R.string.achievement_first));
        }
    }

    // Called whenever the Squash game stops.
    public void onGameStop(SquashView v) {
        if (isSignedIn() && v.mScore > 0) {
            getGamesClient().incrementAchievement(
                    getResources().getString(R.string.achievement_20), v.mScore);

            getGamesClient().submitScore(
                    getResources().getString(R.string.leaderboard_bounces),
                    v.mScore);

            String score = String.valueOf(v.mScore);
            getAppStateClient().updateState(LAST_SCORE_STATE, score.getBytes());
        }
    }

    // Set the login button visible or not
    public void setSigninButtonState() {
        if (isSignedIn()) {
            findViewById(R.id.sign_out_button).setVisibility(View.VISIBLE);
            findViewById(R.id.sign_in_button).setVisibility(View.GONE);
            findViewById(R.id.share_button).setVisibility(View.VISIBLE);
        } else {
            findViewById(R.id.sign_out_button).setVisibility(View.GONE);
            findViewById(R.id.sign_in_button).setVisibility(View.VISIBLE);
            findViewById(R.id.share_button).setVisibility(View.GONE);
        }
    }

    @Override
    public void onSignInFailed() {
        setSigninButtonState();
    }

    @Override
    public void onSignInSucceeded() {
        setSigninButtonState();

        getAppStateClient().loadState(this, LAST_SCORE_STATE);
    }

    @Override
    public void onStateConflict(int stateKey, String ver,
            byte[] localData, byte[] serverData) {
        Log.d("MultiSquash", "state conflict");
        try {
            int local = Integer.parseInt(new String(localData, "UTF-8"));
            int server = Integer.parseInt(new String(serverData, "UTF-8"));
            // select data that has the highest score:
            getAppStateClient().resolveState(this, stateKey, ver,
                    local > server ? localData : serverData);
        } catch (UnsupportedEncodingException ex) {
            Log.w("SquashActivity", "*** Error resolving conflict! (unsupported encoding)");
            ex.printStackTrace();
        } catch (NumberFormatException ex) {
            Log.w("SquashActivity", "*** Error resolving conflict! (parse error)");
            ex.printStackTrace();
        }
        // Normally, you'd resolve the conflict and call
        // mAppStateClient.resolveConflict()
        Log.d("MultiSquash", "state conflict");
    }

    @Override
    public void onStateLoaded(int statusCode, int stateKey, byte[] data) {
        Log.e("MultiSquash", "onGamesLoaded");
        if (statusCode == AppStateClient.STATUS_OK) {
            Log.d("MultiSquash", "loaded: " + data.toString());
            try {
                String s = new String(data, "UTF-8");
                SquashView sv = (SquashView) findViewById(R.id.squashView);
                sv.mScore = Integer.parseInt(s);
            } catch (Exception e) {
                e.printStackTrace();
            }
        } else {
            Log.e("MultiSquash", "failed because: " + statusCode);
        }
    }
}
