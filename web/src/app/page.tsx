"use client";
import Link from 'next/link';
import { motion } from 'framer-motion';

export default function Home() {
  return (
    <main className="flex min-h-screen flex-col items-center justify-center p-4 md:p-24 bg-slate-950 text-white relative overflow-hidden">
      
      {/* Subtle animated background grid */}
      <div className="absolute inset-0 bg-[linear-gradient(to_right,#8080800a_1px,transparent_1px),linear-gradient(to_bottom,#8080800a_1px,transparent_1px)] bg-[size:4rem_4rem] [mask-image:radial-gradient(ellipse_60%_60%_at_50%_50%,#000_70%,transparent_100%)]" />

      <motion.div 
        initial={{ opacity: 0, y: 20 }}
        animate={{ opacity: 1, y: 0 }}
        transition={{ duration: 0.8, ease: "easeOut" }}
        className="z-10 flex flex-col items-center text-center space-y-8 w-full max-w-lg bg-slate-900/40 p-12 rounded-3xl backdrop-blur-xl border border-slate-800/50 shadow-[0_0_60px_rgba(0,0,0,0.5)]"
      >
        {/* Logo / Title Area */}
        <div className="flex flex-col items-center justify-center">
          <div className="w-32 h-32 mb-8 rounded-full overflow-hidden shadow-2xl drop-shadow-[0_0_20px_rgba(75,115,153,0.3)] flex items-center justify-center bg-white border-4 border-slate-800">
            <img src="/logo.png" alt="Chessoc Logo" className="w-full h-full object-cover scale-[1.35]" />
          </div>
          <h1 className="text-6xl font-black tracking-tighter bg-gradient-to-br from-white via-slate-200 to-slate-400 bg-clip-text text-transparent drop-shadow-sm">
            Chessoc
          </h1>
        </div>
        
        <Link 
          href="/room/test" 
          className="mt-4 px-10 py-4 bg-gradient-to-b from-[#5c8aab] to-[#4b7399] text-white font-bold rounded-xl hover:from-[#6a9bc2] hover:to-[#5785b0] transition-all duration-300 shadow-[0_0_20px_rgba(75,115,153,0.4)] hover:shadow-[0_0_30px_rgba(75,115,153,0.6)] border border-[#7caecc]"
        >
          Enter Test Room
        </Link>
      </motion.div>
    </main>
  );
}
