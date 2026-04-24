'use client'

import { redirect } from 'next/navigation'
import { useEffect } from 'react'
import { useRouter } from 'next/navigation'

export default function Variant1Root() {
  const router = useRouter()
  useEffect(() => { router.replace('/variant-1/feed') }, [router])
  return null
}
