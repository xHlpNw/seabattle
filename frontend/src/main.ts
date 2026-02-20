import { bootstrapApplication } from '@angular/platform-browser';
import { App } from './app/app.component';
import { routes } from './app/app.routes';
import { HTTP_INTERCEPTORS, HttpClientModule } from '@angular/common/http';
import { AuthInterceptor } from './app/core/auth/auth.interceptor';
import { provideRouter } from '@angular/router';
import { APP_INITIALIZER, importProvidersFrom } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { AuthService } from './app/core/auth/auth.service';

bootstrapApplication(App, {
  providers: [
    provideRouter(routes),
    importProvidersFrom(HttpClientModule, FormsModule),
    {
      provide: HTTP_INTERCEPTORS,
      useClass: AuthInterceptor,
      multi: true
    },
    {
      provide: APP_INITIALIZER,
      useFactory: (auth: AuthService) => () => auth.validateToken(),
      deps: [AuthService],
      multi: true
    }
  ]
});
