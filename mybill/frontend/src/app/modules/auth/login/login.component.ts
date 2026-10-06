import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Router, ActivatedRoute } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { ApiService } from '../../../core/services/api.service';

@Component({
  selector: 'app-login',
  template: `
    <div class="portal-root">
      <!-- Background Micro-Grid & Ambient Light Accents -->
      <div class="ambient-canvas" aria-hidden="true">
        <div class="ambient-glow glow-top"></div>
        <div class="ambient-glow glow-bottom"></div>
        <div class="grid-pattern"></div>
      </div>

      <!-- Top Utility / Brand Navigation Bar -->
      <header class="top-nav">
        <div class="nav-container">
          <div class="brand-identity">
            <div class="brand-logo-mark">
              <mat-icon class="brand-icon">local_shipping</mat-icon>
            </div>
            <div class="brand-meta">
              <span class="brand-name">
                {{ companyName || 'mybuddy Express' }}
              </span>
              <span class="brand-tagline">LOGISTICS & COURIER PORTAL</span>
            </div>
          </div>

          <div class="nav-status-items">
            <div class="system-status-pill" title="Real-time hub operations active">
              <span class="pulse-beacon"></span>
              <span class="status-label">All Systems Operational</span>
            </div>
            <div class="security-chip">
              <mat-icon class="chip-icon">verified_user</mat-icon>
              <span>256-Bit SSL</span>
            </div>
          </div>
        </div>
      </header>

      <!-- Main Content Layout -->
      <main class="main-stage">
        <div class="stage-container">
          
          <!-- LEFT HERO COLUMN: Telemetry, Courier Animation & Logistics Capabilities -->
          <section class="logistics-hero-section">
            <div class="hero-badge">
              <span class="badge-dot"></span>
              <span>Enterprise Delivery Operations Platform</span>
            </div>

            <h1 class="hero-title">
              Intelligent Courier &amp; Freight Management
            </h1>
            <p class="hero-description">
              Real-time consignment dispatching, automated weight-slab rating, AWB tracking, and instant invoicing built for modern commerce.
            </p>

            <!-- LIVE COURIER ROUTE ANIMATION SCENE -->
            <div class="delivery-visualizer-card">
              <div class="visualizer-header">
                <div class="telemetry-info">
                  <span class="telemetry-tag">ACTIVE DISPATCH ROUTE</span>
                  <span class="awb-track-no">AWB #EXP-8842-IN</span>
                </div>
                <div class="transit-badge">
                  <span class="transit-pulse"></span>
                  <span>In-Flight / Transit</span>
                </div>
              </div>

              <!-- SVG Animated Delivery Corridor -->
              <div class="route-svg-wrapper">
                <svg class="route-svg" viewBox="0 0 520 120" fill="none" xmlns="http://www.w3.org/2000/svg">
                  <defs>
                    <linearGradient id="expressPathGrad" x1="0%" y1="0%" x2="100%" y2="0%">
                      <stop offset="0%" stop-color="#2563EB" />
                      <stop offset="50%" stop-color="#0EA5E9" />
                      <stop offset="100%" stop-color="#10B981" />
                    </linearGradient>
                    <filter id="courierShadow" x="-25%" y="-25%" width="150%" height="150%">
                      <feDropShadow dx="0" dy="3" stdDeviation="3" flood-color="#0F172A" flood-opacity="0.16"/>
                    </filter>
                  </defs>

                  <!-- Base Guide Route Line (Dashed) -->
                  <path d="M 40 60 C 140 15, 230 105, 360 40 C 410 15, 455 45, 485 60"
                        stroke="#E2E8F0" stroke-width="4" stroke-dasharray="6 6" stroke-linecap="round" fill="none" />

                  <!-- Active Glowing Route Dash Line -->
                  <path d="M 40 60 C 140 15, 230 105, 360 40 C 410 15, 455 45, 485 60"
                        class="animated-route-line"
                        stroke="url(#expressPathGrad)" stroke-width="4" stroke-linecap="round" fill="none" />

                  <!-- Origin Hub Node (Fulfillment Hub) -->
                  <g transform="translate(40, 60)">
                    <circle r="14" fill="#2563EB" fill-opacity="0.14" class="hub-radar" />
                    <circle r="6" fill="#2563EB" stroke="#FFFFFF" stroke-width="2.5" />
                  </g>

                  <!-- Mid-way Transit Sorting Node -->
                  <g transform="translate(245, 68)">
                    <circle r="11" fill="#0EA5E9" fill-opacity="0.18" />
                    <circle r="5" fill="#0EA5E9" stroke="#FFFFFF" stroke-width="2" />
                  </g>

                  <!-- Destination Node (Last-Mile Delivery) -->
                  <g transform="translate(485, 60)">
                    <circle r="14" fill="#10B981" fill-opacity="0.16" class="hub-radar" />
                    <circle r="6" fill="#10B981" stroke="#FFFFFF" stroke-width="2.5" />
                  </g>

                  <!-- ANIMATED COURIER VEHICLE & PARCEL -->
                  <g class="animated-courier-vehicle" filter="url(#courierShadow)">
                    <!-- Delivery Van Body -->
                    <rect x="-14" y="-10" width="28" height="20" rx="5" fill="#1E293B" />
                    <!-- Cab Window -->
                    <path d="M 4 -7 L 9 -3 L 9 -1 L 4 -1 Z" fill="#7DD3FC" />
                    <!-- Parcel Accent Box inside van -->
                    <rect x="-10" y="-5" width="8" height="8" rx="2" fill="#2563EB" />
                    <path d="M -7 -5 L -7 3 M -10 -1 L -2 -1" stroke="#FFFFFF" stroke-width="0.9" />
                    <!-- Van Wheels -->
                    <circle cx="-7" cy="10" r="2.8" fill="#0F172A" stroke="#FFFFFF" stroke-width="1.2" />
                    <circle cx="7" cy="10" r="2.8" fill="#0F172A" stroke="#FFFFFF" stroke-width="1.2" />
                  </g>
                </svg>
              </div>

              <!-- Route Checkpoint Stations -->
              <div class="route-stations">
                <div class="station-item">
                  <span class="station-dot origin"></span>
                  <div class="station-text">
                    <span class="station-title">Origin Hub</span>
                    <span class="station-code">BLR Center</span>
                  </div>
                </div>
                <div class="station-item mid">
                  <span class="station-dot transit"></span>
                  <div class="station-text">
                    <span class="station-title">Express Transit</span>
                    <span class="station-code">Sorting Gateway</span>
                  </div>
                </div>
                <div class="station-item end">
                  <span class="station-dot dest"></span>
                  <div class="station-text">
                    <span class="station-title">Destination</span>
                    <span class="station-code">Doorstep Delivery</span>
                  </div>
                </div>
              </div>

              <!-- Live Route Stats Bar -->
              <div class="visualizer-footer">
                <div class="stat-pill">
                  <mat-icon class="stat-icon">schedule</mat-icon>
                  <span>Est. Delivery: <strong>Today, 4:30 PM</strong></span>
                </div>
                <div class="stat-pill">
                  <mat-icon class="stat-icon">bolt</mat-icon>
                  <span>Speed: <strong>Priority Air/Surface</strong></span>
                </div>
              </div>
            </div>

            <!-- REIMAGINED FEATURES: E-Commerce Style Benefit Cards -->
            <div class="feature-strip">
              <div class="benefit-card">
                <div class="benefit-icon-wrapper blue">
                  <mat-icon>inventory_2</mat-icon>
                </div>
                <div class="benefit-details">
                  <h3 class="benefit-title">AWB &amp; Parcel Tracking</h3>
                  <p class="benefit-desc">Live milestone scans and automated customer dispatch notifications.</p>
                </div>
              </div>

              <div class="benefit-card">
                <div class="benefit-icon-wrapper indigo">
                  <mat-icon>alt_route</mat-icon>
                </div>
                <div class="benefit-details">
                  <h3 class="benefit-title">Multi-Zone Rate Engine</h3>
                  <p class="benefit-desc">Automated slab rates, volumetric calculation, and instant zone tariffs.</p>
                </div>
              </div>

              <div class="benefit-card">
                <div class="benefit-icon-wrapper emerald">
                  <mat-icon>receipt_long</mat-icon>
                </div>
                <div class="benefit-details">
                  <h3 class="benefit-title">Consignment Billing</h3>
                  <p class="benefit-desc">Single-click manifest generation, client billing, and GST compliance.</p>
                </div>
              </div>
            </div>
          </section>

          <!-- RIGHT COLUMN: Modern White E-Commerce Sign-In Card -->
          <section class="login-card-section">
            <div class="login-card">
              
              <!-- Card Header -->
              <div class="card-header">
                <div class="login-lock-badge">
                  <mat-icon>lock</mat-icon>
                </div>
                <h2 class="card-title">Sign In to Account</h2>
                <p class="card-subtitle">
                  Enter your operator credentials to access courier consignments, billing &amp; manifests.
                </p>
              </div>

              <!-- Form -->
              <form [formGroup]="loginForm" (ngSubmit)="onSubmit()" class="login-form">
                
                <!-- User ID Field -->
                <div class="field-container">
                  <label class="input-label" for="userIdInput">User ID / Username</label>
                  <mat-form-field appearance="outline" class="custom-field">
                    <mat-icon matPrefix class="field-prefix-icon">person_outline</mat-icon>
                    <input matInput id="userIdInput" formControlName="email" type="text" placeholder="Enter username or operator ID" required autocomplete="username">
                    <mat-error *ngIf="loginForm.get('email')?.hasError('required')">User ID is required</mat-error>
                  </mat-form-field>
                </div>

                <!-- Password Field -->
                <div class="field-container">
                  <div class="label-split">
                    <label class="input-label" for="passwordInput">Password</label>
                  </div>
                  <mat-form-field appearance="outline" class="custom-field">
                    <mat-icon matPrefix class="field-prefix-icon">lock_outline</mat-icon>
                    <input matInput id="passwordInput" formControlName="password" [type]="hidePassword ? 'password' : 'text'" placeholder="Enter account password" required autocomplete="current-password">
                    <button mat-icon-button matSuffix (click)="hidePassword = !hidePassword" type="button" class="visibility-toggle-btn" aria-label="Toggle password visibility" tabIndex="-1">
                      <mat-icon class="toggle-icon">{{ hidePassword ? 'visibility_off' : 'visibility' }}</mat-icon>
                    </button>
                    <mat-error *ngIf="loginForm.get('password')?.hasError('required')">Password is required</mat-error>
                  </mat-form-field>
                </div>

                <!-- Session Expired Alert -->
                <div *ngIf="expiredToken" class="feedback-banner banner-warning" role="alert">
                  <mat-icon class="banner-icon">schedule</mat-icon>
                  <div class="banner-text">
                    <strong>Session Expired</strong>
                    <span>Your session timed out. Please sign in again to continue.</span>
                  </div>
                </div>

                <!-- Authentication Error Alert -->
                <div *ngIf="error && !expiredToken" class="feedback-banner banner-danger" role="alert">
                  <mat-icon class="banner-icon">error_outline</mat-icon>
                  <div class="banner-text">
                    <strong>Authentication Failed</strong>
                    <span>{{ error }}</span>
                  </div>
                </div>

                <!-- Submit CTA Button -->
                <button mat-flat-button type="submit" class="cta-submit-btn" [disabled]="loginForm.invalid || loading">
                  <div class="cta-inner">
                    <mat-spinner *ngIf="loading" diameter="20" class="btn-spinner"></mat-spinner>
                    <span *ngIf="!loading">Access Courier Portal</span>
                    <mat-icon *ngIf="!loading" class="cta-arrow">arrow_forward</mat-icon>
                  </div>
                </button>
              </form>

              <!-- Trust & Security Credentials Footer -->
              <div class="card-trust-footer">
                <mat-icon class="shield-icon">security</mat-icon>
                <span>End-to-End Encrypted Session • Enterprise Logistics Cloud</span>
              </div>
            </div>
          </section>

        </div>
      </main>

      <!-- Global Footer -->
      <footer class="portal-footer">
        <div class="footer-container">
          <p class="copyright-text">
            &copy; {{ currentYear }} {{ companyName || 'mybuddy Express' }}. All rights reserved. Enterprise Logistics Platform v2.4
          </p>
          <div class="footer-links">
            <span class="footer-item">AWB Tracking Engine</span>
            <span class="footer-separator">•</span>
            <span class="footer-item">Rate Calculator</span>
            <span class="footer-separator">•</span>
            <span class="footer-item">Support Desk</span>
          </div>
        </div>
      </footer>
    </div>
  `,
  styles: [`
    @import url('https://fonts.googleapis.com/css2?family=Plus+Jakarta+Sans:wght@400;500;600;700;800&display=swap');

    :host {
      display: block;
      width: 100%;
      min-height: 100vh;
      font-family: 'Plus Jakarta Sans', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif;
      color: #0F172A;
      background: #F8FAFC;
    }

    /* --- ROOT CONTAINER --- */
    .portal-root {
      min-height: 100vh;
      display: flex;
      flex-direction: column;
      position: relative;
      background: #F8FAFC;
    }

    /* --- AMBIENT BACKGROUND --- */
    .ambient-canvas {
      position: absolute;
      inset: 0;
      pointer-events: none;
      z-index: 0;
      overflow: hidden;
    }

    .grid-pattern {
      position: absolute;
      inset: 0;
      background-image: radial-gradient(#CBD5E1 1.2px, transparent 1.2px);
      background-size: 28px 28px;
      opacity: 0.55;
    }

    .ambient-glow {
      position: absolute;
      border-radius: 50%;
      filter: blur(120px);
      opacity: 0.45;
    }

    .glow-top {
      width: 650px;
      height: 650px;
      background: #DBEAFE;
      top: -200px;
      left: -150px;
    }

    .glow-bottom {
      width: 700px;
      height: 700px;
      background: #E0E7FF;
      bottom: -250px;
      right: -200px;
    }

    /* --- TOP NAVIGATION BAR --- */
    .top-nav {
      position: relative;
      z-index: 10;
      border-bottom: 1px solid #E2E8F0;
      background: rgba(255, 255, 255, 0.85);
      backdrop-filter: blur(12px);
      -webkit-backdrop-filter: blur(12px);
      padding: 14px 24px;
    }

    .nav-container {
      max-width: 1340px;
      margin: 0 auto;
      display: flex;
      align-items: center;
      justify-content: space-between;
    }

    .brand-identity {
      display: flex;
      align-items: center;
      gap: 14px;
    }

    .brand-logo-mark {
      width: 44px;
      height: 44px;
      border-radius: 12px;
      background: linear-gradient(135deg, #2563EB 0%, #1D4ED8 100%);
      display: flex;
      align-items: center;
      justify-content: center;
      box-shadow: 0 4px 14px rgba(37, 99, 235, 0.28);
    }

    .brand-icon {
      color: #FFFFFF;
      font-size: 24px;
      width: 24px;
      height: 24px;
    }

    .brand-meta {
      display: flex;
      flex-direction: column;
    }

    .brand-name {
      font-size: 19px;
      font-weight: 800;
      letter-spacing: -0.4px;
      color: #0F172A;
      line-height: 1.2;
    }

    .brand-tagline {
      font-size: 10.5px;
      font-weight: 700;
      letter-spacing: 1.4px;
      color: #64748B;
      text-transform: uppercase;
      margin-top: 1px;
    }

    .nav-status-items {
      display: flex;
      align-items: center;
      gap: 12px;
    }

    .system-status-pill {
      display: flex;
      align-items: center;
      gap: 8px;
      background: #F0FDF4;
      border: 1px solid #BBF7D0;
      padding: 6px 14px;
      border-radius: 9999px;
      font-size: 12.5px;
      font-weight: 600;
      color: #15803D;
    }

    .pulse-beacon {
      width: 8px;
      height: 8px;
      border-radius: 50%;
      background: #16A34A;
      box-shadow: 0 0 0 0 rgba(22, 163, 74, 0.7);
      animation: beaconPulse 2s infinite cubic-bezier(0.66, 0, 0, 1);
    }

    @keyframes beaconPulse {
      to {
        box-shadow: 0 0 0 8px rgba(22, 163, 74, 0);
      }
    }

    .security-chip {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 12px;
      font-weight: 600;
      color: #64748B;
      background: #F1F5F9;
      border: 1px solid #E2E8F0;
      padding: 6px 12px;
      border-radius: 9999px;
    }

    .chip-icon {
      font-size: 16px;
      width: 16px;
      height: 16px;
      color: #2563EB;
    }

    /* --- MAIN HERO STAGE --- */
    .main-stage {
      position: relative;
      z-index: 5;
      flex: 1;
      display: flex;
      align-items: center;
      justify-content: center;
      padding: 48px 24px;
      box-sizing: border-box;
    }

    .stage-container {
      width: 100%;
      max-width: 1300px;
      display: grid;
      grid-template-columns: 1.15fr 0.85fr;
      gap: 56px;
      align-items: center;
    }

    /* --- LEFT HERO SECTION --- */
    .logistics-hero-section {
      display: flex;
      flex-direction: column;
    }

    .hero-badge {
      display: inline-flex;
      align-items: center;
      gap: 8px;
      background: #EFF6FF;
      border: 1px solid #BFDBFE;
      color: #1D4ED8;
      font-size: 12px;
      font-weight: 700;
      letter-spacing: 0.3px;
      padding: 6px 14px;
      border-radius: 9999px;
      width: fit-content;
      margin-bottom: 20px;
    }

    .badge-dot {
      width: 6px;
      height: 6px;
      border-radius: 50%;
      background: #2563EB;
    }

    .hero-title {
      font-size: 38px;
      font-weight: 800;
      line-height: 1.2;
      letter-spacing: -1px;
      color: #0F172A;
      margin: 0 0 16px 0;
    }

    .hero-description {
      font-size: 16px;
      line-height: 1.6;
      color: #475569;
      margin: 0 0 32px 0;
      max-width: 580px;
    }

    /* --- COURIER ROUTE VISUALIZER CARD --- */
    .delivery-visualizer-card {
      background: #FFFFFF;
      border: 1px solid #E2E8F0;
      border-radius: 20px;
      padding: 24px;
      box-shadow: 0 10px 28px -6px rgba(15, 23, 42, 0.05),
                  0 1px 3px rgba(15, 23, 42, 0.03);
      margin-bottom: 32px;
    }

    .visualizer-header {
      display: flex;
      align-items: center;
      justify-content: space-between;
      margin-bottom: 16px;
      padding-bottom: 14px;
      border-bottom: 1px solid #F1F5F9;
    }

    .telemetry-info {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    .telemetry-tag {
      font-size: 10px;
      font-weight: 700;
      letter-spacing: 1.2px;
      color: #64748B;
      text-transform: uppercase;
    }

    .awb-track-no {
      font-size: 15px;
      font-weight: 700;
      color: #0F172A;
      letter-spacing: -0.2px;
    }

    .transit-badge {
      display: flex;
      align-items: center;
      gap: 6px;
      background: #EFF6FF;
      color: #2563EB;
      border: 1px solid #DBEAFE;
      font-size: 12px;
      font-weight: 600;
      padding: 4px 12px;
      border-radius: 9999px;
    }

    .transit-pulse {
      width: 7px;
      height: 7px;
      border-radius: 50%;
      background: #2563EB;
      box-shadow: 0 0 0 0 rgba(37, 99, 235, 0.6);
      animation: beaconPulse 1.8s infinite;
    }

    .route-svg-wrapper {
      width: 100%;
      padding: 8px 0;
    }

    .route-svg {
      width: 100%;
      height: 105px;
      overflow: visible;
    }

    .hub-radar {
      animation: radarPulse 2.2s infinite ease-in-out;
      transform-origin: center;
    }

    @keyframes radarPulse {
      0%, 100% { transform: scale(1); opacity: 0.2; }
      50% { transform: scale(1.5); opacity: 0.6; }
    }

    .animated-route-line {
      stroke-dasharray: 12 8;
      animation: flowRouteDash 3s linear infinite;
    }

    @keyframes flowRouteDash {
      to {
        stroke-dashoffset: -40;
      }
    }

    /* Courier Van Traveling Along Bezier Curve Path */
    .animated-courier-vehicle {
      offset-path: path("M 40 60 C 140 15, 230 105, 360 40 C 410 15, 455 45, 485 60");
      offset-rotate: auto;
      animation: driveRoute 7s infinite ease-in-out;
    }

    @keyframes driveRoute {
      0% { offset-distance: 0%; }
      50% { offset-distance: 52%; }
      85% { offset-distance: 100%; }
      100% { offset-distance: 100%; }
    }

    /* Route Checkpoint Labels */
    .route-stations {
      display: flex;
      justify-content: space-between;
      margin-top: 14px;
      padding: 0 10px;
    }

    .station-item {
      display: flex;
      align-items: flex-start;
      gap: 8px;
    }

    .station-dot {
      width: 9px;
      height: 9px;
      border-radius: 50%;
      margin-top: 4px;
      flex-shrink: 0;
    }

    .station-dot.origin { background: #2563EB; }
    .station-dot.transit { background: #0EA5E9; }
    .station-dot.dest { background: #10B981; }

    .station-text {
      display: flex;
      flex-direction: column;
    }

    .station-title {
      font-size: 12px;
      font-weight: 700;
      color: #0F172A;
    }

    .station-code {
      font-size: 11px;
      color: #64748B;
      font-weight: 500;
    }

    .visualizer-footer {
      display: flex;
      align-items: center;
      gap: 16px;
      margin-top: 18px;
      padding-top: 14px;
      border-top: 1px solid #F1F5F9;
    }

    .stat-pill {
      display: flex;
      align-items: center;
      gap: 6px;
      font-size: 12px;
      color: #475569;
    }

    .stat-icon {
      font-size: 16px;
      width: 16px;
      height: 16px;
      color: #2563EB;
    }

    /* --- BENEFIT CARDS STRIP --- */
    .feature-strip {
      display: grid;
      grid-template-columns: repeat(3, 1fr);
      gap: 16px;
    }

    .benefit-card {
      background: #FFFFFF;
      border: 1px solid #E2E8F0;
      border-radius: 14px;
      padding: 16px;
      display: flex;
      flex-direction: column;
      gap: 10px;
      transition: all 0.2s ease;
    }

    .benefit-card:hover {
      transform: translateY(-2px);
      box-shadow: 0 10px 20px -5px rgba(15, 23, 42, 0.06);
      border-color: #CBD5E1;
    }

    .benefit-icon-wrapper {
      width: 38px;
      height: 38px;
      border-radius: 10px;
      display: flex;
      align-items: center;
      justify-content: center;
    }

    .benefit-icon-wrapper.blue {
      background: #EFF6FF;
      color: #2563EB;
    }

    .benefit-icon-wrapper.indigo {
      background: #EEF2FF;
      color: #4F46E5;
    }

    .benefit-icon-wrapper.emerald {
      background: #ECFDF5;
      color: #059669;
    }

    .benefit-icon-wrapper mat-icon {
      font-size: 20px;
      width: 20px;
      height: 20px;
    }

    .benefit-title {
      font-size: 13.5px;
      font-weight: 700;
      color: #0F172A;
      margin: 0 0 4px 0;
      line-height: 1.3;
    }

    .benefit-desc {
      font-size: 11.5px;
      line-height: 1.45;
      color: #64748B;
      margin: 0;
    }

    /* --- RIGHT COLUMN: LOGIN CARD --- */
    .login-card-section {
      display: flex;
      justify-content: center;
    }

    .login-card {
      width: 100%;
      max-width: 480px;
      background: #FFFFFF;
      border: 1px solid #E2E8F0;
      border-radius: 24px;
      padding: 40px;
      box-shadow: 0 20px 45px -10px rgba(15, 23, 42, 0.08),
                  0 1px 3px rgba(15, 23, 42, 0.03);
      box-sizing: border-box;
    }

    .card-header {
      margin-bottom: 28px;
    }

    .login-lock-badge {
      width: 44px;
      height: 44px;
      border-radius: 12px;
      background: #EFF6FF;
      color: #2563EB;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-bottom: 16px;
    }

    .login-lock-badge mat-icon {
      font-size: 22px;
      width: 22px;
      height: 22px;
    }

    .card-title {
      font-size: 24px;
      font-weight: 800;
      letter-spacing: -0.5px;
      color: #0F172A;
      margin: 0 0 8px 0;
    }

    .card-subtitle {
      font-size: 13.5px;
      color: #64748B;
      line-height: 1.5;
      margin: 0;
    }

    /* Form Fields */
    .login-form {
      display: flex;
      flex-direction: column;
      gap: 16px;
    }

    .field-container {
      display: flex;
      flex-direction: column;
    }

    .label-split {
      display: flex;
      justify-content: space-between;
      align-items: center;
    }

    .input-label {
      font-size: 13px;
      font-weight: 600;
      color: #1E293B;
      margin-bottom: 6px;
    }

    .custom-field {
      width: 100%;
    }

    /* MDC Form Field Tuning */
    ::ng-deep .custom-field.mat-mdc-form-field {
      --mdc-outlined-text-field-outline-color: #CBD5E1;
      --mdc-outlined-text-field-focus-outline-color: #2563EB;
      --mdc-outlined-text-field-hover-outline-color: #94A3B8;
      --mdc-outlined-text-field-container-shape: 12px;
      --mdc-outlined-text-field-input-text-color: #0F172A;
      --mdc-outlined-text-field-input-text-placeholder-color: #94A3B8;
    }

    ::ng-deep .custom-field .mat-mdc-text-field-wrapper {
      background-color: #F8FAFC !important;
      border-radius: 12px !important;
      transition: background-color 0.2s ease, box-shadow 0.2s ease;
    }

    ::ng-deep .custom-field.mat-focused .mat-mdc-text-field-wrapper {
      background-color: #FFFFFF !important;
      box-shadow: 0 0 0 3px rgba(37, 99, 235, 0.12) !important;
    }

    .field-prefix-icon {
      color: #64748B;
      margin-right: 8px;
      font-size: 20px;
      width: 20px;
      height: 20px;
    }

    .visibility-toggle-btn {
      color: #64748B;
    }

    .visibility-toggle-btn:hover {
      color: #0F172A;
    }

    .toggle-icon {
      font-size: 20px;
      width: 20px;
      height: 20px;
    }

    /* Feedback Alert Banners */
    .feedback-banner {
      display: flex;
      align-items: flex-start;
      gap: 12px;
      padding: 12px 14px;
      border-radius: 10px;
      font-size: 13px;
      line-height: 1.4;
      animation: fadeIn 0.25s ease;
    }

    .banner-warning {
      background: #FFFBEB;
      border: 1px solid #FDE68A;
      color: #92400E;
    }

    .banner-danger {
      background: #FEF2F2;
      border: 1px solid #FECACA;
      color: #B91C1C;
    }

    .banner-icon {
      font-size: 20px;
      width: 20px;
      height: 20px;
      flex-shrink: 0;
      margin-top: 1px;
    }

    .banner-text {
      display: flex;
      flex-direction: column;
      gap: 2px;
    }

    /* Submit CTA Button */
    .cta-submit-btn {
      width: 100%;
      height: 48px;
      border-radius: 12px !important;
      background: linear-gradient(135deg, #2563EB 0%, #1D4ED8 100%) !important;
      color: #FFFFFF !important;
      font-size: 14.5px !important;
      font-weight: 700 !important;
      letter-spacing: 0.2px !important;
      margin-top: 8px;
      box-shadow: 0 4px 14px rgba(37, 99, 235, 0.35) !important;
      transition: all 0.2s cubic-bezier(0.4, 0, 0.2, 1) !important;
      border: none !important;
      cursor: pointer;
    }

    .cta-submit-btn:hover:not([disabled]) {
      background: linear-gradient(135deg, #1D4ED8 0%, #1E40AF 100%) !important;
      box-shadow: 0 6px 20px rgba(37, 99, 235, 0.45) !important;
      transform: translateY(-1px);
    }

    .cta-submit-btn:active:not([disabled]) {
      transform: translateY(1px);
      box-shadow: 0 2px 8px rgba(37, 99, 235, 0.3) !important;
    }

    .cta-submit-btn[disabled] {
      background: #E2E8F0 !important;
      color: #94A3B8 !important;
      box-shadow: none !important;
      cursor: not-allowed;
    }

    .cta-inner {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 8px;
      width: 100%;
    }

    .cta-arrow {
      font-size: 18px;
      width: 18px;
      height: 18px;
      transition: transform 0.2s ease;
    }

    .cta-submit-btn:hover:not([disabled]) .cta-arrow {
      transform: translateX(3px);
    }

    .btn-spinner {
      margin: 0 auto;
    }

    ::ng-deep .btn-spinner circle {
      stroke: #FFFFFF !important;
    }

    /* Trust & Security Footer */
    .card-trust-footer {
      display: flex;
      align-items: center;
      justify-content: center;
      gap: 6px;
      margin-top: 24px;
      font-size: 11.5px;
      color: #64748B;
      font-weight: 500;
    }

    .shield-icon {
      font-size: 15px;
      width: 15px;
      height: 15px;
      color: #10B981;
    }

    /* --- GLOBAL FOOTER --- */
    .portal-footer {
      position: relative;
      z-index: 10;
      border-top: 1px solid #E2E8F0;
      background: rgba(255, 255, 255, 0.7);
      backdrop-filter: blur(8px);
      padding: 16px 24px;
    }

    .footer-container {
      max-width: 1340px;
      margin: 0 auto;
      display: flex;
      align-items: center;
      justify-content: space-between;
      font-size: 12px;
      color: #64748B;
    }

    .copyright-text {
      margin: 0;
    }

    .footer-links {
      display: flex;
      align-items: center;
      gap: 8px;
    }

    .footer-separator {
      color: #CBD5E1;
    }

    /* --- PREFERS REDUCED MOTION --- */
    @media (prefers-reduced-motion: reduce) {
      .pulse-beacon,
      .transit-pulse,
      .hub-radar,
      .animated-route-line,
      .animated-courier-vehicle {
        animation: none !important;
      }
    }

    /* --- RESPONSIVE BREAKPOINTS --- */
    @media (max-width: 1100px) {
      .stage-container {
        grid-template-columns: 1fr;
        gap: 40px;
      }

      .logistics-hero-section {
        order: 2;
      }

      .login-card-section {
        order: 1;
      }

      .hero-title {
        font-size: 30px;
      }
    }

    @media (max-width: 768px) {
      .top-nav {
        padding: 12px 16px;
      }

      .nav-status-items {
        display: none;
      }

      .main-stage {
        padding: 24px 16px;
      }

      .feature-strip {
        grid-template-columns: 1fr;
      }

      .login-card {
        padding: 24px 20px;
      }

      .footer-container {
        flex-direction: column;
        gap: 8px;
        text-align: center;
      }
    }

    @keyframes fadeIn {
      from { opacity: 0; transform: translateY(4px); }
      to { opacity: 1; transform: translateY(0); }
    }
  `]
})
export class LoginComponent implements OnInit {
  loginForm: FormGroup;
  loading = false;
  error = '';
  expiredToken = false;
  hidePassword = true;
  companyName: string = '';
  currentYear: number = new Date().getFullYear();

  constructor(
    private fb: FormBuilder,
    private authService: AuthService,
    private router: Router,
    private route: ActivatedRoute,
    private apiService: ApiService
  ) {
    this.authService.logout();
    
    this.loginForm = this.fb.group({
      email: ['', Validators.required],
      password: ['', Validators.required]
    });
  }

  ngOnInit() {
    this.route.queryParams.subscribe(params => {
      if (params['expired'] === 'true') {
        this.expiredToken = true;
        this.error = 'Your session has expired. Please login again.';
      }
    });
    this.loadCompanyName();
  }
  
  loadCompanyName() {
    this.apiService.get<any>('/company-settings').subscribe({
      next: (settings) => {
        if (settings?.companyName) {
          this.companyName = settings.companyName;
          document.title = this.companyName;
        }
      },
      error: () => {
        this.companyName = '';
      }
    });
  }

  onSubmit() {
    if (this.loginForm.valid) {
      this.loading = true;
      this.error = '';
      
      this.authService.login(this.loginForm.value).subscribe({
        next: (response) => {
          const startTime = Date.now();
          const checkToken = setInterval(() => {
            const token = this.authService.getToken();
            if (token) {
              clearInterval(checkToken);
              this.loading = false;
              this.router.navigate(['/dashboard']).then(
                (success) => {
                  if (!success) {
                    this.error = 'Navigation failed. Please try again.';
                    this.loading = false;
                  }
                }
              ).catch((err) => {
                this.error = `Navigation error: ${err.message || 'Unknown error'}`;
                this.loading = false;
              });
            } else if (Date.now() - startTime > 2000) {
              clearInterval(checkToken);
              this.error = 'Token storage failed. Please try again.';
              this.loading = false;
            }
          }, 50);
        },
        error: (err) => {
          this.error = err.error?.message || err.message || 'Login failed. Please check your credentials.';
          this.loading = false;
        }
      });
    }
  }
}
