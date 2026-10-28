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
          "Account information. When you choose Google or Facebook sign-in, Plotus receives the provider account identifier, name, and any email address and profile-picture URL the provider supplies. These identify your Plotus account and display your account details. Plotus does not receive your Google or Facebook password.",
          "Login and security information. Plotus keeps account and session identifiers, session expiry and revocation records, and credentials needed to keep you signed in and protect the service. If you add a passkey, Plotus stores its public credential information and related security metadata. Your fingerprint, face data and device unlock code are not sent to Plotus.",
          "Project information. When signed in, you can save project names, locations, plot boundaries and measurements, north direction, road details and optional client names, email addresses and phone numbers. Plotus stores these details on its backend under your account so you can access them on iPhone and web. Only provide client information you are authorised to use. The separate iPhone design preview uses temporary local sample projects.",
          "Sketch images. When you choose Upload plot image, Plotus resizes the selected photo and sends it to the configured image-reading provider, currently Meta, to extract plot measurements. Plotus holds the image in memory during analysis and does not retain it on disk. The server’s temporary analysis results expire after roughly ten minutes. Extracted measurements are saved to your project only when you choose to save them.",
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
          "We use account and session information to provide sign-in, show your account, manage passkeys, prevent abuse and troubleshoot service problems. We use your saved project and client information to provide your workspace across devices. We use support messages to respond to your request. We do not sell your personal information or use it for targeted advertising.",
          "Information is processed by the services needed to provide these functions, including our hosting infrastructure, Google when you choose Google sign-in, and Meta when you choose Facebook sign-in. These providers also process information under their own privacy policies. Information may be disclosed when legally required or when necessary to protect people and the service.",
          "The private floor-plan engine is not connected to the current app. Sketch extraction sends only the photo you choose, with extraction instructions, to Meta’s image-understanding API. Existing project and client records are not included in that request. The provider processes the image under its own terms. Check the extracted outline and measurements before saving them.",
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
          "Saved projects remain in your account until you delete them or request account deletion. iPhone preview edits last only for the running preview session. Account and passkey records remain until they are removed; signing out does not delete your account. Login sessions expire after seven days, but expiry does not itself delete the stored account or session records. Support correspondence is kept as needed to handle your request and follow-up.",
          "You can remove a saved passkey in Account settings, sign out, clear local browser data, or revoke Plotus access in your Google or Facebook account. Removing provider access and deleting your Plotus account are separate actions.",
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
          "Plotus is being built to help architects and residential designers organise a home-design brief and explore early planning ideas. The current app lets signed-in users save project and client details, define plot boundaries, set north and roads, search and sort projects, and change appearance. Google, Facebook and passkeys provide access to your Plotus account.",
          "Signed-in project and plot changes are saved to your account and available across devices. Sketch-image reading is available for review and correction. Floor-plan requirements and generation are still in development. Descriptions of that direction are not a promise that a feature is available today or will ship on a particular date.",
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
          "Choose Start project on the website and sign in to open your saved workspace. Create a project, define its boundary and measurements, set north and add road details. You can also edit project details, search, sort and change the theme. A separate iPhone design preview contains temporary local sample projects.",
          "Google or Facebook sign-in opens your Plotus account. You can add and remove passkeys in Settings → Account on iPhone or web. Upload a photo of a plot sketch to extract and review its measurements. Floor-plan requirements and generation are still in development.",
        ],
      },
      {
        title: "Having trouble signing in?",
        bullets: [
          "Check your connection, close the sign-in prompt and start again. A cancelled or expired sign-in attempt can be restarted safely.",
          "Use the same Google or Facebook account you used to create your Plotus account.",
          "To use a passkey, sign in with Google or Facebook first and add one in Account settings on a compatible browser or iPhone. Removing a passkey revokes its Plotus registration; if an old entry remains in your password manager, remove it there too.",
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
          "A full account-deletion request covers the Plotus profile and provider identifier, saved projects and client details, stored sessions and passkey credentials associated with that account. You can also ask about information you sent to support. Temporary iPhone preview edits stay in the running session.",
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
          "These instructions cover Facebook sign-in and any Plotus Facebook test authorisation you may have granted.",
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
          "Deleting the account removes its Plotus profile, provider identifier, sessions, stored passkey credentials and saved project, plot and client details. If you also want support correspondence deleted, include that in your request.",
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
