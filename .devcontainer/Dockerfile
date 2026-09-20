###############################################################################
# Devcontainer image for building linphone-android
# (https://github.com/BelledonneCommunications/linphone-android)
#
# Modeled directly on the project's own upstream CI image
# (docker-files/bc-dev-android-36 in this repo), which is what
# .gitlab-ci-files/job-android.yml uses to run `./gradlew assembleDebug` /
# `assembleRelease`. Notable difference: the Android platform is bumped to 37
# to match app/build.gradle.kts's current `compileSdk`/`targetSdk`.
###############################################################################

FROM debian:trixie

LABEL maintainer="jakub@icewarp.com"
LABEL description="Build environment for linphone-android"

# Configure locale
RUN apt-get update && \
    apt-get install -y --no-install-recommends locales ca-certificates && \
    echo "en_US.UTF-8 UTF-8" > /etc/locale.gen && \
    locale-gen && \
    apt-get clean && rm -rf /var/lib/apt/lists/*
ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en' LC_ALL='en_US.UTF-8'
ENV SHELL=/bin/bash

ENV ANDROID_HOME=/opt/android-sdk-linux
ENV ANDROID_SDK_ROOT=$ANDROID_HOME
ENV JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64

# Install general + development tools.
# openjdk-21-jdk-headless matches the JDK used by upstream's own
# bc-dev-android-36 image (required by Gradle 9.7.x / AGP 9.4.x used here).
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        curl nano sudo unzip vim wget rsync openssh-client git \
        openjdk-21-jdk-headless && \
    apt-get clean && rm -rf /var/lib/apt/lists/*

# Android command line tools (same build upstream pins: 9477386)
RUN mkdir -p $ANDROID_HOME/cmdline-tools/latest && \
    cd /tmp && \
    wget -q https://dl.google.com/android/repository/commandlinetools-linux-9477386_latest.zip && \
    unzip -q commandlinetools-linux-9477386_latest.zip && \
    cp -R cmdline-tools/* $ANDROID_HOME/cmdline-tools/latest/ && \
    rm -rf /tmp/cmdline-tools /tmp/commandlinetools-linux-9477386_latest.zip

ENV PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH

# Only platform-tools is pre-installed. The app's compileSdk/targetSdk (37)
# now maps to versioned platform packages (e.g. "platforms;android-37.2")
# rather than a plain "platforms;android-37", and that mapping can shift
# over time -- so instead of guessing, licenses are pre-accepted here and
# AGP is left to auto-download the exact platform/build-tools packages it
# resolves for compileSdk 37 on first `./gradlew` invocation (requires
# network access to dl.google.com/maven.google.com).
RUN yes | sdkmanager --licenses > /dev/null && \
    sdkmanager --install "platform-tools" > /dev/null

RUN chmod -R ugo+rwx $ANDROID_HOME

# Consider all git repositories as safe (bind-mounted workspace owned by host uid)
RUN git config --system --add safe.directory '*'

ARG USERNAME=bc
RUN useradd -ms /bin/bash $USERNAME && \
    echo "$USERNAME ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers

USER $USERNAME
WORKDIR /home/$USERNAME
ENV PS1='\[\e[34m\]\u@linphone-android-devcontainer>\[\e[0m\] '
CMD ["bash"]
