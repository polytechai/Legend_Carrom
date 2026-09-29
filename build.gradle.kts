workflows:
  android-workflow:
    name: Android Build
    max_build_duration: 60
    instance_type: mac_mini_m1
    scripts:
      - name: Set up local.properties
        script: |
          echo "sdk.dir=$ANDROID_SDK_ROOT" > local.properties
      - name: Generate Gradle Wrapper and Build APK
        script: |
          if [ ! -f gradlew ]; then
            gradle wrapper
          fi
          chmod +x gradlew
          ./gradlew assembleDebug
    artifacts:
      - app/build/outputs/**/*.apk
