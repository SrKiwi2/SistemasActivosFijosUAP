import type { CapacitorConfig } from '@capacitor/cli';

const config: CapacitorConfig = {
  appId: 'bo.edu.uap.sciaf',
  appName: 'SCIAF',
  webDir: 'dist',
  android: {
    // La app se sirve desde https://localhost dentro del WebView. Llamar a un
    // backend http:// (el de pruebas en la LAN) es "contenido mixto" y el
    // WebView lo bloquearía. Con esto se permite.
    //
    // En producción el backend es https://sciaf.uap.edu.bo y esto puede
    // volver a false — junto con las excepciones de
    // android/app/src/main/res/xml/network_security_config.xml.
    allowMixedContent: true,
  },
  server: {
    androidScheme: 'https',
  },
  plugins: {
    SplashScreen: {
      launchShowDuration: 1200,
      backgroundColor: '#144391',
      androidScaleType: 'CENTER_CROP',
      showSpinner: false,
    },
  },
};

export default config;
