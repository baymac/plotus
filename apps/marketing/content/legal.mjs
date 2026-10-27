export const contactEmail = "hello@plotus.app";

const contact = `mailto:${contactEmail}`;
const deletionRequest = `${contact}?subject=${encodeURIComponent("Plotus data deletion request")}`;
const accountRequest = `${contact}?subject=${encodeURIComponent("Delete my Plotus account")}`;
const updated = "28 October 2026";

export const legalLinks = [
  { label: "Privacy", href: "/privacy/" },
  { label: "Terms", href: "/terms/" },
  { label: "Support", href: "/support/" },
  { label: "Data deletion", href: "/data-deletion/" },
  { label: "Delete account", href: "/account-deletion/" },
];

export const legalPages = [
  {
    path: "/privacy/",
    eyebrow: "Privacy",
    title: "Your ideas deserve care.",
    intro:
      "This policy explains how Plotus handles information on this website and in the current iPhone and web demo.",
    updated,
    sections: [
      {
        title: "Who to contact",
        paragraphs: [
          "Plotus is a home-planning workspace for architects and residential designers. For privacy questions, access or correction requests, or concerns about your information, contact the Plotus team at hello@plotus.app.",
        ],
        links: [{ label: contactEmail, href: contact }],
      },
      {
        title: "Information used by the current app",
        bullets: [
          "Account information. When you choose Google sign-in, Plotus receives your Google account identifier, email address, name and profile-picture URL. These identify your Plotus account and display your account details. Plotus does not receive your Google password.",
          "Login and security information. Plotus keeps account and session identifiers, session expiry and revocation records, and credentials needed to keep you signed in and protect the service. If you add a passkey, Plotus stores its public credential information and related security metadata. Your fingerprint, face data and device unlock code are not sent to Plotus.",
          "Demo project information. The demo lets you enter project names, locations and optional client names, email addresses and phone numbers. These edits stay in the running demo session; they are not uploaded to the project backend or synced between devices. Use sample information when exploring the demo.",
          "Support messages. If you email us, we receive your email address, message and any attachments you choose to send. Please avoid sending passwords, login codes, identity documents or confidential client material.",
        ],
      },
      {
        title: "Website, cookies and device storage",
        paragraphs: [
          "Visiting this marketing website does not require an account. We do not add advertising pixels or analytics trackers to it. Cloudflare delivers and protects the website and processes technical request information such as IP address, requested URL, browser information and security signals as part of that service.",
          "The web app uses necessary cookies for sign-in and login security. It stores appearance and sidebar preferences in your browser. The iPhone app stores preferences on the device and uses the iOS Keychain for its Plotus login session. You can clear website data through your browser settings; doing so may sign you out and reset preferences.",
        ],
        links: [
          {
            label: "Cloudflare privacy policy",
            href: "https://www.cloudflare.com/privacypolicy/",
          },
        ],
      },
      {
        title: "How information is used and shared",
        paragraphs: [
          "We use account and session information to provide sign-in, show your account, manage passkeys, prevent abuse and troubleshoot service problems. We use support messages to respond to your request. We do not sell your personal information or use it for targeted advertising.",
          "Information is processed by the services needed to provide these functions, including our hosting infrastructure and Google when you choose Google sign-in. Google also processes information under its own privacy policy. Information may be disclosed when legally required or when necessary to protect people and the service.",
          "The planning engine is not connected to the current demo. Demo project and client information is not sent to an AI or planning service. Facebook Login is still in development and is not a sign-in option in the current release. We will update this policy before enabling additional collection or uses of information.",
        ],
        links: [
          {
            label: "Google privacy policy",
            href: "https://policies.google.com/privacy",
          },
        ],
      },
      {
        title: "Retention and your choices",
        paragraphs: [
          "Demo project edits last only for the running demo session. Account and passkey records remain until they are removed; signing out does not delete your account. Login sessions expire after seven days, but expiry does not itself delete the stored account or session records. Support correspondence is kept as needed to handle your request and follow-up.",
          "You can remove a saved passkey in Account settings, sign out, clear local browser data, or revoke Plotus access in your Google account. Removing provider access and deleting your Plotus account are separate actions.",
          "You can request access, correction or deletion by emailing hello@plotus.app. Account and data deletion are currently handled manually. We may verify that the request is yours before making changes. We will explain the scope and expected completion time in our reply, confirm completion, and explain any information that must be retained and why. Requests are available to all users, wherever they are located.",
        ],
        links: [
          { label: "Data deletion instructions", href: "/data-deletion/" },
          { label: "Account deletion", href: "/account-deletion/" },
          {
            label: "Manage Google connections",
            href: "https://myaccount.google.com/connections",
          },
        ],
      },
      {
        title: "Changes to this policy",
        paragraphs: [
          "This policy describes the current website and demo. When the way Plotus handles information changes, we will update this page and its date. Material changes that need your consent will be presented before the new processing begins.",
        ],
      },
    ],
    action: {
      label: "Ask a privacy question",
      href: `${contact}?subject=Plotus%20privacy%20question`,
    },
    related: [
      { label: "Data deletion", href: "/data-deletion/" },
      { label: "Support", href: "/support/" },
    ],
  },
  {
    path: "/terms/",
    eyebrow: "Terms of use",
    title: "A clear starting point.",
    intro:
      "These terms explain the current Plotus website and demo, what you can expect from them, and how to use them responsibly.",
    updated,
    sections: [
      {
        title: "The current service",
        paragraphs: [
          "Plotus is being built to help architects and residential designers organise a home-design brief and explore early planning ideas. The current demo lets you explore sample projects, edit project and client details, search and sort projects, and change appearance. Google sign-in and web passkeys provide access to a real Plotus account.",
          "Demo project changes last for the current session. Saved projects, plot setup, room requirements and floor-plan generation are still in development. Descriptions of that direction are not a promise that a feature is available today or will ship on a particular date.",
        ],
      },
      {
        title: "Your account and information",
        paragraphs: [
          "Use an account you are authorised to use and keep your device and sign-in methods secure. Contact us if you believe your Plotus account has been accessed without permission. Do not share passwords, login codes or session credentials with us.",
          "You keep your rights to information you enter. Only provide client or other personal information when you have permission to do so. Use sample information in the demo, and keep separate copies of any work you need to retain.",
        ],
        links: [{ label: "Read the privacy policy", href: "/privacy/" }],
      },
      {
        title: "Use Plotus responsibly",
        bullets: [
          "Do not use the service unlawfully or submit material that infringes someone else's rights.",
          "Do not attempt to access another person's account, bypass security, introduce malicious software, or disrupt the service.",
          "Do not present illustrative plans or early concepts as surveyed, engineered, approved or construction-ready documents.",
        ],
      },
      {
        title: "Design decisions remain yours",
        paragraphs: [
          "Plotus supports early design exploration. It does not replace a measured site survey, an architect's or engineer's professional judgement, local authority approvals, or checks against building regulations. Any future concept output must be independently reviewed before it is used for permissions, costing or construction.",
        ],
      },
      {
        title: "Availability and changes",
        paragraphs: [
          "This is an evolving demo. Features may change and the service may be interrupted. Do not rely on it as your only record of a project or as a production design or construction system.",
          "We may restrict access where needed to address misuse, security or legal obligations. These terms do not exclude rights or protections you have under applicable consumer law.",
        ],
      },
      {
        title: "Questions and account deletion",
        paragraphs: [
          "Contact hello@plotus.app with questions about these terms or the service. To request deletion of a Plotus account, follow the account-deletion instructions. We will update this page when these terms change.",
        ],
        links: [
          {
            label: "Account deletion instructions",
            href: "/account-deletion/",
          },
        ],
      },
    ],
    action: { label: "Contact Plotus", href: contact },
    related: [{ label: "Privacy", href: "/privacy/" }],
  },
  {
    path: "/support/",
    eyebrow: "Support",
    title: "Let’s get you unstuck.",
    intro:
      "Questions, a bug, or an idea for a better planning workspace? Reach the Plotus team at hello@plotus.app.",
    updated,
    sections: [
      {
        title: "Contact the team",
        paragraphs: [
          "Email hello@plotus.app for help with the iPhone app, web demo or your Plotus account. Include your device or browser, what you were trying to do, and what happened. A screenshot can help; remove any confidential client information first.",
          "We will never ask you to send a password, login code, passkey, or session credential.",
        ],
        links: [{ label: contactEmail, href: contact }],
      },
      {
        title: "What the demo can do today",
        paragraphs: [
          "Choose View demo to explore sample projects without signing in. You can create and edit project details, search, sort and change the theme. Demo project changes are temporary and do not sync across devices.",
          "Google sign-in opens a real Plotus account. Web accounts can add and remove passkeys in Account settings. Saved projects, plot setup, room requirements and generated floor plans are still in development. Facebook Login is not available in the current release.",
        ],
      },
      {
        title: "Having trouble signing in?",
        bullets: [
          "Check your connection, close the sign-in prompt and start again. A cancelled or expired Google attempt can be restarted safely.",
          "Use the same Google account you used to create your Plotus account.",
          "To use a web passkey, sign in with Google first and add one in Account settings on a compatible browser and device.",
          "If the problem continues, email the error message and your device or browser details. Do not include login codes or credentials.",
        ],
      },
      {
        title: "Privacy and deletion",
        paragraphs: [
          "You can request a copy or correction of your information, deletion of specific data, or deletion of your entire Plotus account. The pages below explain the current request process and how to remove a provider connection.",
        ],
        links: [
          { label: "Privacy policy", href: "/privacy/" },
          { label: "Delete data", href: "/data-deletion/" },
          { label: "Delete account", href: "/account-deletion/" },
        ],
      },
    ],
    action: {
      label: "Email Plotus support",
      href: `${contact}?subject=Plotus%20support`,
    },
    related: [{ label: "All policies", href: "/legal/" }],
  },
  {
    path: "/data-deletion/",
    eyebrow: "Data deletion",
    title: "Your data. Your decision.",
    intro:
      "Request deletion of information associated with Plotus, including information connected to a social sign-in test. You do not need to delete your Google or Facebook account.",
    updated,
    sections: [
      {
        title: "Send a deletion request",
        paragraphs: [
          "Data deletion is currently handled manually by the Plotus team. This page provides request instructions; it is not an automated deletion callback.",
        ],
        steps: [
          "Email hello@plotus.app with the subject “Plotus data deletion request”, preferably from the email address associated with your Plotus account.",
          "Tell us whether you want specific information removed or your entire Plotus account deleted. If the request concerns a Facebook Login test, mention Facebook and any app-scoped user ID already available to you. Do not send your Facebook password or access token.",
          "We will reply to verify ownership where needed, explain what will be removed and the expected completion time, and confirm when the request is complete. If anything must be retained, we will explain what and why.",
        ],
      },
      {
        title: "What your request can cover",
        paragraphs: [
          "A full account-deletion request covers the Plotus profile and provider identifier, stored sessions and passkey credentials associated with that account. You can also ask about information you sent to support. Demo project edits stay in the running session and are not saved to the project backend.",
          "Deleting data from Plotus does not delete your account with Google, Facebook or another provider. Signing out, uninstalling the app, or removing a provider connection does not by itself delete a stored Plotus account.",
        ],
        links: [
          {
            label: "Delete your entire Plotus account",
            href: "/account-deletion/",
          },
        ],
      },
      {
        title: "Remove a provider connection",
        paragraphs: [
          "For Google, open the connections section of your Google account, select Plotus and remove its access. For Facebook, open Settings, find Apps and websites, select Plotus if it appears and remove it. You can do this separately from a deletion request to Plotus.",
          "Facebook Login is in development and is not offered by the current public demo. These instructions also cover any Plotus Facebook test authorization you may have granted.",
        ],
        links: [
          {
            label: "Google account connections",
            href: "https://myaccount.google.com/connections",
          },
          {
            label: "Facebook apps and websites",
            href: "https://www.facebook.com/settings?tab=applications",
          },
        ],
      },
    ],
    action: { label: "Request data deletion", href: deletionRequest },
    related: [
      { label: "Privacy policy", href: "/privacy/" },
      { label: "Support", href: "/support/" },
    ],
  },
  {
    path: "/account-deletion/",
    eyebrow: "Account deletion",
    title: "Leave with a clear next step.",
    intro:
      "You can ask the Plotus team to delete your account and its associated information. The current demo uses a manual request process.",
    updated,
    sections: [
      {
        title: "Request account deletion",
        steps: [
          "Email hello@plotus.app with the subject “Delete my Plotus account”. Use the email address associated with the account where possible.",
          "Confirm that you want your entire Plotus account deleted. Do not include a password, login code, passkey or provider access token.",
          "We will reply with any ownership-verification steps and an expected completion time. We will confirm completion and explain any information that must be retained and why.",
        ],
      },
      {
        title: "What deletion means",
        paragraphs: [
          "Deleting the account removes its Plotus profile, provider identifier, sessions and stored passkey credentials. The current app does not save project edits to an account. If you also want support correspondence deleted, include that in your request.",
          "Deleting your Plotus account does not delete your Google or Facebook account. Removing Plotus from a provider's connected apps revokes that connection; it is separate from deleting information held by Plotus.",
        ],
      },
      {
        title: "Before you leave",
        paragraphs: [
          "Keep a separate copy of any demo information you want to retain. Signing out or uninstalling the app is not an account-deletion request. The current app does not yet have an in-app account-deletion control; use the contact action below to send a request.",
        ],
        links: [
          {
            label: "Provider access and data deletion instructions",
            href: "/data-deletion/",
          },
        ],
      },
    ],
    action: { label: "Request account deletion", href: accountRequest },
    related: [
      { label: "Privacy policy", href: "/privacy/" },
      { label: "Contact support", href: "/support/" },
    ],
  },
  {
    path: "/contact/",
    eyebrow: "Contact",
    title: "Good spaces start with a conversation.",
    intro:
      "Talk to the Plotus team about the product, share feedback, or get help with your account.",
    updated,
    sections: [
      {
        title: "Write to us",
        paragraphs: [
          "Reach us at hello@plotus.app. Whether you design homes, are planning one, or want to work with Plotus, tell us a little about what you have in mind.",
          "For an app issue, include your device or browser and a short description of what happened. Please keep confidential client information and login credentials out of your message.",
        ],
        links: [{ label: contactEmail, href: contact }],
      },
    ],
    action: { label: "Say hello", href: contact },
    related: [
      { label: "Product support", href: "/support/" },
      { label: "Privacy and policies", href: "/legal/" },
    ],
  },
  {
    path: "/legal/",
    eyebrow: "Privacy & policies",
    title: "The details, in one place.",
    intro:
      "Understand how Plotus handles information, find help, or request deletion of your data and account.",
    updated,
    sections: [
      {
        title: "Your information and choices",
        paragraphs: [
          "Read how the current website and demo work, which information is used, and how to contact the team about your privacy.",
        ],
        links: legalLinks,
      },
      {
        title: "A person to contact",
        paragraphs: [
          "For policy questions or product help, email the Plotus team at hello@plotus.app.",
        ],
        links: [{ label: contactEmail, href: contact }],
      },
    ],
    action: { label: "Contact Plotus", href: contact },
  },
];
