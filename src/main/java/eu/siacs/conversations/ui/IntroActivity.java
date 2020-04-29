package eu.siacs.conversations.ui;

import android.content.Intent;
import android.os.Bundle;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.github.paolorotolo.appintro.AppIntro;
import com.github.paolorotolo.appintro.AppIntroFragment;
import com.github.paolorotolo.appintro.model.SliderPage;

import eu.siacs.conversations.R;

import static eu.siacs.conversations.ui.util.IntroHelper.SaveIntroShown;

public class IntroActivity extends AppIntro {
    public static final String ACTIVITY = "activity";
    public static final String MULTICHAT = "multi_chat";
    public static final String START_UI = "StartUI";
    public static final String WELCOME_ACTIVITY = "WelcomeActivity";
    public static final String START_CONVERSATION_ACTIVITY = "StartConversationActivity";
    public static final String CONVERSATIONS_ACTIVITY = "ConversationsActivity";
    public static final String CONTACT_DETAILS_ACTIVITY = "ContactDetailsActivity";
    public static final String CONFERENCE_DETAILS_ACTIVITY = "ConferenceDetailsActivity";
    String activity = null;
    boolean mode_multi = false;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        final int backgroundColor = getResources().getColor(R.color.primary_dark);
        final int barColor = getResources().getColor(R.color.primary);

        setBarColor(barColor);
        setProgressButtonEnabled(true);
        showSkipButton(false);
        setBackButtonVisibilityWithDone(true);
        setGoBackLock(true);
        setFadeAnimation();

        final Intent intent = getIntent();
        if (intent != null) {
            activity = intent.getStringExtra(ACTIVITY);
            mode_multi = intent.getBooleanExtra(MULTICHAT, false);
        }
        if (activity == null) {
            finish();
        }
        switch (activity) {
            case START_UI:
                SliderPage welcome = new SliderPage();
                welcome.setTitle(getString(R.string.welcome_header));
                welcome.setDescription(getString(R.string.intro_desc_main));
                welcome.setImageDrawable(R.drawable.main_logo);
                welcome.setBgColor(backgroundColor);
                addSlide(AppIntroFragment.newInstance(welcome));

                SliderPage privacy = new SliderPage();
                privacy.setTitle(getString(R.string.intro_privacy));
                privacy.setDescription(getString(R.string.intro_desc_privacy));
                privacy.setImageDrawable(R.drawable.intro_security_icon);
                privacy.setBgColor(backgroundColor);
                addSlide(AppIntroFragment.newInstance(privacy));


                SliderPage xmpp = new SliderPage();
                xmpp.setTitle(getString(R.string.intro_whats_xmpp));
                xmpp.setDescription(getString(R.string.intro_desc_whats_xmpp));
                xmpp.setImageDrawable(R.drawable.intro_xmpp_icon);
                xmpp.setBgColor(backgroundColor);
                addSlide(AppIntroFragment.newInstance(xmpp));

                SliderPage permissions = new SliderPage();
                permissions.setTitle(getString(R.string.intro_required_permissions));
                permissions.setDescription(getString(R.string.intro_desc_required_permissions));
                permissions.setImageDrawable(R.drawable.intro_memory_icon);
                permissions.setBgColor(backgroundColor);
                addSlide(AppIntroFragment.newInstance(permissions));

                SliderPage permissions2 = new SliderPage();
                permissions2.setTitle(getString(R.string.intro_optional_permissions));
                permissions2.setDescription(getString(R.string.intro_desc_optional_permissions));
                permissions2.setImageDrawable(R.drawable.intro_contacts_icon);
                permissions2.setBgColor(backgroundColor);
                addSlide(AppIntroFragment.newInstance(permissions2));

                SliderPage permissions3 = new SliderPage();
                permissions3.setTitle(getString(R.string.intro_optional_permissions));
                permissions3.setDescription(getString(R.string.intro_desc_optional_permissions2));
                permissions3.setImageDrawable(R.drawable.intro_location_icon);
                permissions3.setBgColor(backgroundColor);
                addSlide(AppIntroFragment.newInstance(permissions3));
                break;
            case WELCOME_ACTIVITY:
                SliderPage account = new SliderPage();
                account.setTitle(getString(R.string.intro_account));
                account.setDescription(getString(R.string.intro_desc_account));
                account.setImageDrawable(R.drawable.intro_account_icon);
                account.setBgColor(backgroundColor);
                addSlide(AppIntroFragment.newInstance(account));

                SliderPage account2 = new SliderPage();
                account2.setTitle(getString(R.string.intro_account));
                account2.setDescription(getString(R.string.intro_desc_account2));
                account2.setImageDrawable(R.drawable.intro_account_icon);
                account2.setBgColor(backgroundColor);
                addSlide(AppIntroFragment.newInstance(account2));

                SliderPage account3 = new SliderPage();
                account3.setTitle(getString(R.string.intro_account));
                account3.setDescription(getString(R.string.intro_desc_account3));
                account3.setImageDrawable(R.drawable.intro_account_icon);
                account3.setBgColor(backgroundColor);
                addSlide(AppIntroFragment.newInstance(account3));
                break;
            case START_CONVERSATION_ACTIVITY:
                SliderPage startChatting = new SliderPage();
                startChatting.setTitle(getString(R.string.intro_start_chatting));
                startChatting.setDescription(getString(R.string.intro_desc_start_chatting));
                startChatting.setImageDrawable(R.drawable.intro_start_chat_icon);
                startChatting.setBgColor(backgroundColor);
                addSlide(AppIntroFragment.newInstance(startChatting));

                SliderPage startChatting2 = new SliderPage();
                startChatting2.setTitle(getString(R.string.intro_start_chatting));
                startChatting2.setDescription(getString(R.string.intro_desc_start_chatting2));
                startChatting2.setImageDrawable(R.drawable.intro_start_chat_icon);
                startChatting2.setBgColor(backgroundColor);
                addSlide(AppIntroFragment.newInstance(startChatting2));

                SliderPage startChatting3 = new SliderPage();
                startChatting3.setTitle(getString(R.string.intro_start_chatting));
                startChatting3.setDescription(getString(R.string.intro_desc_start_chatting3));
                startChatting3.setImageDrawable(R.drawable.intro_start_chat_icon);
                startChatting3.setBgColor(backgroundColor);
                addSlide(AppIntroFragment.newInstance(startChatting3));
                break;
            case CONVERSATIONS_ACTIVITY:
                SliderPage openChat = new SliderPage();
                openChat.setTitle(getString(R.string.intro_start_chatting));
                openChat.setDescription(getString(R.string.intro_desc_open_chat));
                openChat.setImageDrawable(R.drawable.intro_start_chat_icon);
                openChat.setBgColor(backgroundColor);
                addSlide(AppIntroFragment.newInstance(openChat));

                SliderPage chatDetails = new SliderPage();
                chatDetails.setTitle(getString(R.string.intro_chat_details));
                chatDetails.setDescription(getString(R.string.intro_desc_chat_details));
                chatDetails.setImageDrawable(R.drawable.intro_account_details_icon);
                chatDetails.setBgColor(backgroundColor);
                addSlide(AppIntroFragment.newInstance(chatDetails));

                if (mode_multi) {
                    SliderPage highlightUser = new SliderPage();
                    highlightUser.setTitle(getString(R.string.intro_highlight_user));
                    highlightUser.setDescription(getString(R.string.intro_desc_highlight_user));
                    highlightUser.setImageDrawable(R.drawable.intro_account_details_icon);
                    highlightUser.setBgColor(backgroundColor);
                    addSlide(AppIntroFragment.newInstance(highlightUser));
                }
                break;
            case CONTACT_DETAILS_ACTIVITY:
            case CONFERENCE_DETAILS_ACTIVITY:
                SliderPage openChatDetails = new SliderPage();
                openChatDetails.setTitle(getString(R.string.intro_chat_details));
                openChatDetails.setDescription(getString(R.string.intro_desc_open_chat_details));
                openChatDetails.setImageDrawable(R.drawable.intro_account_details_icon);
                openChatDetails.setBgColor(backgroundColor);
                addSlide(AppIntroFragment.newInstance(openChatDetails));
        }
    }

    @Override
    public void onSkipPressed(Fragment currentFragment) {
        super.onSkipPressed(currentFragment);
        finish();
    }

    @Override
    public void onDonePressed(Fragment currentFragment) {
        super.onDonePressed(currentFragment);
        SaveIntroShown(getBaseContext(), activity, mode_multi);
        finish();
    }

    @Override
    public void onSlideChanged(@Nullable Fragment oldFragment, @Nullable Fragment newFragment) {
        super.onSlideChanged(oldFragment, newFragment);
    }
}
