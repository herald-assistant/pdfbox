import { bootstrapApplication } from "@angular/platform-browser";
import { provideHttpClient } from "@angular/common/http";
import { registerLocaleData } from "@angular/common";
import localePl from "@angular/common/locales/pl";
import { LOCALE_ID } from "@angular/core";
import { AppComponent } from "./app/app.component";

registerLocaleData(localePl);
bootstrapApplication(AppComponent, {
  providers: [provideHttpClient(), { provide: LOCALE_ID, useValue: "pl" }],
}).catch(console.error);
